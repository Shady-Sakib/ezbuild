package ezbuild.model;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PENDING_PAYMENT("Awaiting payment"),
    PLACED("Placed (cash on delivery)"),
    PAID("Paid"),
    PROCESSING("Processing"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled"),
    EXPIRED("Expired"),
    REFUNDED("Refunded");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Allowed status transitions; OrderService rejects anything else. */
    public Set<OrderStatus> next() {
        return switch (this) {
            case PENDING_PAYMENT -> EnumSet.of(PAID, CANCELLED, EXPIRED);
            case PLACED -> EnumSet.of(PROCESSING, CANCELLED);
            case PAID -> EnumSet.of(PROCESSING, REFUNDED);
            case PROCESSING -> EnumSet.of(SHIPPED, REFUNDED, CANCELLED);
            case SHIPPED -> EnumSet.of(DELIVERED);
            case DELIVERED -> EnumSet.of(REFUNDED);
            default -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return next().contains(target);
    }

    public boolean refundable() {
        return canTransitionTo(REFUNDED);
    }

    public String badge() {
        return switch (this) {
            case PAID, DELIVERED -> "ok";
            case PENDING_PAYMENT, PLACED, PROCESSING, SHIPPED -> "info";
            default -> "muted";
        };
    }
}
