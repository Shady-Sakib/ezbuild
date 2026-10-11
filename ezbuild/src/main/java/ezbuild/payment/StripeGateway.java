package ezbuild.payment;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import ezbuild.config.AppProperties;
import ezbuild.model.Order;
import ezbuild.model.Payment;
import ezbuild.model.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stripe hosted Checkout (test mode). Prices are BDT; Stripe charges USD at the configured rate. Fulfilment
 * is driven by the signed webhook, never by the success page.
 */
@Slf4j
@Component
public class StripeGateway implements PaymentGateway {

    private final AppProperties props;

    public StripeGateway(AppProperties props) {
        this.props = props;
        if (props.stripe().configured()) {
            Stripe.apiKey = props.stripe().secretKey();
        }
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.STRIPE;
    }

    @Override
    public boolean available() {
        return props.stripe().configured();
    }

    /** BDT -> US cents, rounded up so we never under-charge. */
    public long usdCents(long bdt) {
        return (long) Math.ceil(bdt / props.stripe().bdtPerUsd() * 100.0);
    }

    @Override
    public InitResult init(Order order, Payment payment) {
        if (!available()) return InitResult.failed("Stripe is not configured (set STRIPE_SECRET_KEY).");
        long cents = usdCents(order.getTotal());
        payment.setChargedAmount(cents);
        payment.setChargedCurrency("usd");
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(props.baseUrl() + "/checkout/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(props.baseUrl() + "/checkout/cancelled?order=" + order.getCode())
                .setClientReferenceId(order.getCode())
                .setCustomerEmail(order.getEmail())
                .putMetadata("order", order.getCode())
                .putMetadata("tran", payment.getTranId())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount(cents)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("EZBuild order " + order.getCode() + " (৳" + order.getTotal() + ")")
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            Session session = Session.create(params,
                    RequestOptions.builder().setIdempotencyKey("ezb-" + payment.getTranId()).build());
            return InitResult.ok(session.getUrl(), session.getId());
        } catch (StripeException e) {
            log.warn("Stripe session create failed: tran={} {}", payment.getTranId(), e.getMessage());
            return InitResult.failed("Stripe could not start the payment. Please try again.");
        }
    }

    /** Checks the Stripe-Signature header against the raw body; throws on a bad signature. */
    public Event verify(String payload, String signature) throws com.stripe.exception.SignatureVerificationException {
        return Webhook.constructEvent(payload, signature, props.stripe().webhookSecret());
    }

    public Session retrieve(String sessionId) throws StripeException {
        return Session.retrieve(sessionId);
    }

    @Override
    public RefundResult refund(Payment payment, long amountBdt, String reason) {
        if (!available()) return new RefundResult(false, false, null, "Stripe is not configured.");
        if (payment.getPaymentIntent() == null) return new RefundResult(false, false, null, "Missing payment intent.");
        try {
            Refund refund = Refund.create(RefundCreateParams.builder()
                    .setPaymentIntent(payment.getPaymentIntent())
                    .setAmount(Math.min(usdCents(amountBdt), payment.getChargedAmount()))
                    .build());
            boolean done = "succeeded".equals(refund.getStatus());
            return new RefundResult(true, done, refund.getId(), null);
        } catch (StripeException e) {
            log.warn("Stripe refund failed: tran={} {}", payment.getTranId(), e.getMessage());
            return new RefundResult(false, false, null, e.getMessage());
        }
    }
}
