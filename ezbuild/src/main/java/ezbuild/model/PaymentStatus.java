package ezbuild.model;

public enum PaymentStatus {
    UNPAID("Unpaid"),
    COD_PENDING("Cash due on delivery"),
    INITIATED("Initiated"),
    SUCCEEDED("Paid"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
    RISK_HOLD("On hold (risk review)"),
    REFUND_PENDING("Refund pending"),
    REFUNDED("Refunded");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
