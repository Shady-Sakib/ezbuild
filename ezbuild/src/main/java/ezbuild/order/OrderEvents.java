package ezbuild.order;

/** Published inside transactions and handled after commit, so no email goes out for a rolled-back order. */
public final class OrderEvents {

    private OrderEvents() { }

    public record Placed(String orderId) { }

    public record Paid(String orderId) { }

    public record StatusChanged(String orderId, String status) { }
}
