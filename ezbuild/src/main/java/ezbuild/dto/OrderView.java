package ezbuild.dto;

import ezbuild.model.Order;

import java.time.Instant;
import java.util.List;

/** Order as returned by the REST API. */
public record OrderView(String code, String status, String statusLabel, String paymentMethod, String paymentStatus,
                        List<Line> items, long subtotal, long discountTotal, long shippingFee, long codFee, long total,
                        String shippingZone, Instant createdAt, Instant expiresAt) {

    public record Line(String productId, String name, long unitPrice, int qty, long lineTotal) { }

    public static OrderView of(Order o) {
        return new OrderView(o.getCode(), o.getStatus().name(), o.getStatus().getLabel(), o.getPaymentMethod().name(),
                o.getPaymentStatus().name(),
                o.getItems().stream().map(i -> new Line(i.getProductId(), i.getName(), i.getUnitPrice(), i.getQty(),
                        i.lineTotal())).toList(),
                o.getSubtotal(), o.getDiscountTotal(), o.getShippingFee(), o.getCodFee(), o.getTotal(),
                o.getShippingZone() == null ? null : o.getShippingZone().name(), o.getCreatedAt(), o.getExpiresAt());
    }
}
