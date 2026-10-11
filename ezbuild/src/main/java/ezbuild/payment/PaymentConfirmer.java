package ezbuild.payment;

import ezbuild.model.Order;
import ezbuild.model.OrderStatus;
import ezbuild.model.Payment;
import ezbuild.model.PaymentEvent;
import ezbuild.model.PaymentStatus;
import ezbuild.order.OrderService;
import ezbuild.repository.OrderRepository;
import ezbuild.repository.PaymentEventRepository;
import ezbuild.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment confirmation in its own bean so the @Transactional proxy applies (self-calls would bypass it).
 * Idempotent: final payments and already-seen event keys are ignored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmer {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Transactional(rollbackFor = Exception.class)
    public Payment apply(String paymentId, PaymentOutcome outcome, String eventKey, String provider) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.REFUNDED) {
            return payment;
        }
        if (eventKey != null) {
            try {
                eventRepository.insert(new PaymentEvent(eventKey, provider, outcome.getClass().getSimpleName()));
            } catch (DuplicateKeyException e) {
                log.info("Duplicate payment event ignored: {}", eventKey);
                return payment;
            }
        }
        Order order = orderRepository.findById(payment.getOrderId()).orElseThrow();
        switch (outcome) {
            case PaymentOutcome.Succeeded s -> {
                payment.setStatus(PaymentStatus.SUCCEEDED);
                payment.setValId(s.valId());
                payment.setBankTranId(s.bankTranId());
                payment.setPaymentIntent(s.paymentIntent());
                payment.setCardType(s.cardType());
                paymentRepository.save(payment);
                orderService.markPaid(order, provider, "Paid via " + payment.getProvider().getLabel());
                log.info("Payment succeeded: order={} tran={}", order.getCode(), payment.getTranId());
            }
            case PaymentOutcome.RiskHold r -> {
                payment.setStatus(PaymentStatus.RISK_HOLD);
                payment.setValId(r.valId());
                payment.setBankTranId(r.bankTranId());
                payment.setRiskLevel(r.riskLevel());
                paymentRepository.save(payment);
                order.setPaymentStatus(PaymentStatus.RISK_HOLD);
                order.setNeedsAttention(true);
                order.setExpiresAt(null);
                order.getHistory().add(new Order.StatusChange(order.getStatus(), provider, "Payment held for risk review"));
                orderRepository.save(order);
                log.warn("Payment on risk hold: order={} tran={}", order.getCode(), payment.getTranId());
            }
            case PaymentOutcome.Failed f -> {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(f.reason());
                paymentRepository.save(payment);
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    order.setPaymentStatus(PaymentStatus.FAILED);
                    orderRepository.save(order);
                }
            }
            case PaymentOutcome.Cancelled c -> {
                payment.setStatus(PaymentStatus.CANCELLED);
                payment.setFailureReason(c.reason());
                paymentRepository.save(payment);
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    order.setPaymentStatus(PaymentStatus.CANCELLED);
                    orderRepository.save(order);
                }
            }
        }
        return payment;
    }
}
