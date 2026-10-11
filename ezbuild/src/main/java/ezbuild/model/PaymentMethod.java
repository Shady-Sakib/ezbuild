package ezbuild.model;

public enum PaymentMethod {
    COD("Cash on delivery", false),
    SSLCOMMERZ("SSLCommerz (card, bKash, Nagad, bank)", true),
    STRIPE("Stripe (international card)", true),
    SANDBOX("Demo sandbox (offline test)", true);

    private final String label;
    private final boolean digital;

    PaymentMethod(String label, boolean digital) {
        this.label = label;
        this.digital = digital;
    }

    public String getLabel() {
        return label;
    }

    public boolean isDigital() {
        return digital;
    }
}
