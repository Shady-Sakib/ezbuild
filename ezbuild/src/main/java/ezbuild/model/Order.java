package ezbuild.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("orders")
@CompoundIndexes({
        @CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "status_expires", def = "{'status': 1, 'expiresAt': 1}")
})
@Getter
@Setter
public class Order {

    @Id
    private String id;

    /** Short human-friendly code, e.g. EZ7K3P9Q */
    @Indexed(unique = true)
    private String code;

    private String userId;

    private String username;

    private String email;

    /** Price snapshot: later price edits never change old orders. */
    private List<OrderItem> items = new ArrayList<>();

    private Address shipping;

    private ShippingZone shippingZone;

    private long subtotal;

    private long discountTotal;

    private long shippingFee;

    private long codFee;

    private long total;

    private PaymentMethod paymentMethod;

    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    private OrderStatus status;

    private List<StatusChange> history = new ArrayList<>();

    private String note;

    private boolean needsAttention;

    @CreatedDate
    private Instant createdAt;

    /** Digital payments only: unpaid orders release their stock after this. */
    private Instant expiresAt;

    public int itemCount() {
        return items.stream().mapToInt(OrderItem::getQty).sum();
    }

    public boolean payable() {
        return status == OrderStatus.PENDING_PAYMENT && paymentMethod != null && paymentMethod.isDigital();
    }

    @Getter
    @Setter
    public static class OrderItem {
        private String productId;
        private String name;
        private String brand;
        private ProductCategory category;
        private long unitListPrice;
        private int discountPercent;
        private long unitPrice;
        private int qty;

        public long lineTotal() {
            return unitPrice * qty;
        }
    }

    @Getter
    @Setter
    public static class StatusChange {
        private OrderStatus status;
        private Instant at = Instant.now();
        private String by;
        private String note;

        public StatusChange() { }

        public StatusChange(OrderStatus status, String by, String note) {
            this.status = status;
            this.by = by;
            this.note = note;
        }
    }
}
