package ezbuild.payment;

/**
 * Result of verifying a payment with its provider (sealed type; PaymentService switches on it with pattern
 * matching).
 */
public sealed interface PaymentOutcome permits PaymentOutcome.Succeeded, PaymentOutcome.Failed,
        PaymentOutcome.Cancelled, PaymentOutcome.RiskHold {

    record Succeeded(String valId, String bankTranId, String paymentIntent, String cardType) implements PaymentOutcome { }

    record Failed(String reason) implements PaymentOutcome { }

    record Cancelled(String reason) implements PaymentOutcome { }

    record RiskHold(String valId, String bankTranId, String riskLevel) implements PaymentOutcome { }
}
