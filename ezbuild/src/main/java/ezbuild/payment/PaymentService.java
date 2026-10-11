package ezbuild.payment;

import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import ezbuild.model.Order;
import ezbuild.model.OrderStatus;
import ezbuild.model.Payment;
import ezbuild.model.PaymentMethod;
import ezbuild.model.PaymentStatus;
import ezbuild.order.OrderService;
import ezbuild.repository.PaymentRepository;
import ezbuild.service.BusinessException;
import ezbuild.service.NotFoundException;
import ezbuild.util.Hashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Starts payments and verifies results with the provider. The browser is never trusted: SSLCommerz ->
 * Validation API, Stripe -> signed webhook or a server-side Session.retrieve.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGatewayRegistry registry;
    private final SslCommerzGateway sslCommerz;
    private final StripeGateway stripe;
    private final PaymentRepository paymentRepository;
    private final PaymentConfirmer confirmer;
    private final OrderService orderService;

    /** Creates a payment attempt and returns where to send the browser. */
    public String start(Order order) {
        if (!order.payable()) throw new BusinessException("This order is not waiting for payment.");
        PaymentMethod method = order.getPaymentMethod();
        PaymentGateway gateway = registry.get(method);
        if (!gateway.available()) throw new BusinessException(method.getLabel() + " is not available right now.");

        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setOrderCode(order.getCode());
        payment.setUserId(order.getUserId());
        payment.setProvider(method);
        payment.setTranId("EZ-" + order.getCode() + "-" + Hashing.code(4));
        payment.setAmountBdt(order.getTotal());
        payment.setChargedAmount(order.getTotal());
        payment.setChargedCurrency("BDT");
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setCreatedAt(Instant.now());

        PaymentGateway.InitResult result = gateway.init(order, payment);
        if (!result.ok()) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.error());
            paymentRepository.save(payment);
            throw new BusinessException(result.error());
        }
        payment.setProviderRef(result.providerRef());
        paymentRepository.save(payment);
        log.info("Payment started: order={} provider={} tran={}", order.getCode(), method, payment.getTranId());
        return result.redirectUrl();
    }

    // ---- SSLCommerz

    /**
     * Shared by the success redirect and the IPN: find by tran_id, stop if already final, call the Validation
     * API, compare tran_id/amount/currency, risk_level 1 -> hold, else mark paid in one transaction.
     */
    public Payment confirmSslCommerz(Map<String, String> params) {
        String tranId = params.getOrDefault("tran_id", "");
        Payment payment = paymentRepository.findByTranId(tranId)
                .orElseThrow(() -> new NotFoundException("Unknown transaction."));
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) return payment;

        String valId = params.get("val_id");
        if (valId == null || valId.isBlank()) {
            return confirmer.apply(payment.getId(), new PaymentOutcome.Failed("No validation id"), null, "sslcommerz");
        }
        Map<String, Object> v;
        try {
            v = sslCommerz.validate(valId);
        } catch (Exception e) {
            log.warn("SSLCommerz validation call failed: tran={} {}", tranId, e.getMessage());
            return payment; // stays INITIATED; the IPN or reconciliation will settle it
        }
        String status = SslCommerzGateway.str(v.get("status"));
        if (!"VALID".equalsIgnoreCase(status) && !"VALIDATED".equalsIgnoreCase(status)) {
            return confirmer.apply(payment.getId(), new PaymentOutcome.Failed("Validation status " + status), null, "sslcommerz");
        }
        if (!tranId.equals(SslCommerzGateway.str(v.get("tran_id")))
                || !"BDT".equalsIgnoreCase(SslCommerzGateway.str(v.get("currency_type")).isBlank()
                        ? SslCommerzGateway.str(v.get("currency")) : SslCommerzGateway.str(v.get("currency_type")))
                || Math.round(parse(v.get("currency_amount") != null ? v.get("currency_amount") : v.get("amount"))) != payment.getAmountBdt()) {
            log.warn("SSLCommerz amount/currency/tran mismatch: tran={}", tranId);
            return confirmer.apply(payment.getId(), new PaymentOutcome.Failed("Amount or currency mismatch"), null, "sslcommerz");
        }
        String bankTranId = SslCommerzGateway.str(v.get("bank_tran_id"));
        if ("1".equals(SslCommerzGateway.str(v.get("risk_level")))) {
            return confirmer.apply(payment.getId(), new PaymentOutcome.RiskHold(valId, bankTranId, "1"), "sslc:" + valId, "sslcommerz");
        }
        return confirmer.apply(payment.getId(),
                new PaymentOutcome.Succeeded(valId, bankTranId, null, SslCommerzGateway.str(v.get("card_type"))),
                "sslc:" + valId, "sslcommerz");
    }

    public Payment failOrCancel(String tranId, boolean cancelled) {
        Payment payment = paymentRepository.findByTranId(tranId).orElseThrow(() -> new NotFoundException("Unknown transaction."));
        if (payment.getStatus() != PaymentStatus.INITIATED) return payment;
        PaymentOutcome outcome = cancelled ? new PaymentOutcome.Cancelled("Cancelled by customer")
                : new PaymentOutcome.Failed("Declined at the gateway");
        return confirmer.apply(payment.getId(), outcome, null, "sslcommerz");
    }

    // ---- Stripe

    /** Signed webhook. A bad signature throws SignatureVerificationException (-> 400). */
    public void handleStripeWebhook(String payload, String signature) throws com.stripe.exception.SignatureVerificationException {
        Event event = stripe.verify(payload, signature);
        String type = event.getType();
        if (!type.startsWith("checkout.session.")) return;
        Optional<StripeObject> obj = event.getDataObjectDeserializer().getObject();
        if (obj.isEmpty() || !(obj.get() instanceof Session session)) {
            log.warn("Stripe event {} could not be deserialised (API version mismatch?)", event.getId());
            return;
        }
        Payment payment = paymentRepository.findByProviderRef(session.getId()).orElse(null);
        if (payment == null) return;
        switch (type) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
                if ("paid".equals(session.getPaymentStatus())) settleStripe(payment, session, event.getId());
            }
            case "checkout.session.async_payment_failed" ->
                    confirmer.apply(payment.getId(), new PaymentOutcome.Failed("Async payment failed"), event.getId(), "stripe");
            case "checkout.session.expired" ->
                    confirmer.apply(payment.getId(), new PaymentOutcome.Cancelled("Session expired"), event.getId(), "stripe");
            default -> { }
        }
    }

    /** Asks Stripe directly (server to server); used by the success page and reconciliation. */
    public Optional<Payment> confirmStripeSession(String sessionId) {
        Payment payment = paymentRepository.findByProviderRef(sessionId).orElse(null);
        if (payment == null) return Optional.empty();
        if (payment.getStatus() != PaymentStatus.INITIATED || !stripe.available()) return Optional.of(payment);
        try {
            Session session = stripe.retrieve(sessionId);
            if ("paid".equals(session.getPaymentStatus())) settleStripe(payment, session, "stripe-session:" + sessionId);
        } catch (Exception e) {
            log.warn("Stripe session retrieve failed: {}", e.getMessage());
        }
        return paymentRepository.findById(payment.getId());
    }

    private void settleStripe(Payment payment, Session session, String eventKey) {
        if (session.getAmountTotal() == null || session.getAmountTotal() != payment.getChargedAmount()
                || !"usd".equalsIgnoreCase(session.getCurrency())) {
            confirmer.apply(payment.getId(), new PaymentOutcome.Failed("Amount or currency mismatch"), eventKey, "stripe");
            return;
        }
        confirmer.apply(payment.getId(), new PaymentOutcome.Succeeded(session.getId(), null, session.getPaymentIntent(), "card"),
                eventKey, "stripe");
    }

    // ---- Sandbox (offline demo)

    public Payment sandboxDecision(String tranId, String userId, boolean approve) {
        Payment payment = paymentRepository.findByTranId(tranId).orElseThrow(() -> new NotFoundException("Unknown transaction."));
        if (!payment.getUserId().equals(userId) || payment.getProvider() != PaymentMethod.SANDBOX) {
            throw new NotFoundException("Unknown transaction.");
        }
        PaymentOutcome outcome = approve ? new PaymentOutcome.Succeeded("SBX-VAL-" + tranId, "SBX-BANK-" + tranId, null, "DEMO")
                : new PaymentOutcome.Failed("Declined in the sandbox");
        return confirmer.apply(payment.getId(), outcome, "sbx:" + tranId, "sandbox");
    }

    public Payment byTranId(String tranId) {
        return paymentRepository.findByTranId(tranId).orElseThrow(() -> new NotFoundException("Unknown transaction."));
    }

    public List<Payment> forOrder(Order order) {
        return paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
    }

    // ---- Refunds

    /** Admin refund: provider refund for digital payments, then order -> REFUNDED (which restocks). */
    public Order refund(String orderCode, String actor, String reason) {
        Order order = orderService.getByCode(orderCode);
        if (!order.getStatus().canTransitionTo(OrderStatus.REFUNDED)) {
            throw new BusinessException("This order cannot be refunded in its current state.");
        }
        if (order.getPaymentMethod().isDigital()) {
            Payment paid = forOrder(order).stream().filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED).findFirst()
                    .orElseThrow(() -> new BusinessException("No successful payment found for this order."));
            PaymentGateway.RefundResult r = registry.get(paid.getProvider()).refund(paid, order.getTotal(), reason);
            if (!r.ok()) throw new BusinessException("Refund failed: " + r.error());
            paid.setRefundRef(r.ref());
            paid.setStatus(r.completed() ? PaymentStatus.REFUNDED : PaymentStatus.REFUND_PENDING);
            paymentRepository.save(paid);
        }
        Order updated = orderService.changeStatus(orderCode, OrderStatus.REFUNDED, actor,
                reason == null || reason.isBlank() ? "Refunded" : reason);
        updated.setPaymentStatus(PaymentStatus.REFUNDED);
        log.info("Order refunded: code={} by={}", orderCode, actor);
        return orderService.save(updated);
    }

    public List<Payment> attention() {
        return paymentRepository.findByStatusIn(List.of(PaymentStatus.RISK_HOLD, PaymentStatus.REFUND_PENDING));
    }

    // ---- Reconciliation

    /** Every 10 minutes: payments still INITIATED after 15 minutes are checked with the provider. */
    @Scheduled(fixedDelay = 600_000, initialDelay = 120_000)
    public void reconcile() {
        List<Payment> stale = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.INITIATED,
                Instant.now().minus(Duration.ofMinutes(15)));
        for (Payment p : stale) {
            try {
                switch (p.getProvider()) {
                    case SSLCOMMERZ -> {
                        if (!sslCommerz.available()) continue;
                        Map<String, Object> row = sslCommerz.query(p.getTranId());
                        String status = SslCommerzGateway.str(row.get("status"));
                        if ("VALID".equalsIgnoreCase(status) || "VALIDATED".equalsIgnoreCase(status)) {
                            confirmSslCommerz(Map.of("tran_id", p.getTranId(), "val_id", SslCommerzGateway.str(row.get("val_id"))));
                        } else if (row.isEmpty() || "FAILED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
                            confirmer.apply(p.getId(), new PaymentOutcome.Cancelled("Not completed (reconciliation)"), null, "reconcile");
                        }
                    }
                    case STRIPE -> confirmStripeSession(p.getProviderRef());
                    default -> confirmer.apply(p.getId(), new PaymentOutcome.Cancelled("Abandoned"), null, "reconcile");
                }
            } catch (RuntimeException e) {
                log.warn("Reconciliation failed for tran={}: {}", p.getTranId(), e.getMessage());
            }
        }
    }

    private static double parse(Object o) {
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
