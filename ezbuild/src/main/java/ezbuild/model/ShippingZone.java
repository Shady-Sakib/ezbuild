package ezbuild.model;

public enum ShippingZone {
    INSIDE_DHAKA("Inside Dhaka"),
    OUTSIDE_DHAKA("Outside Dhaka");

    private final String label;

    ShippingZone(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
