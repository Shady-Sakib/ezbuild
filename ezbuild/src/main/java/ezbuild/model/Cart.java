package ezbuild.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One cart per user (_id = userId). Ids and quantities only; prices are always re-read. */
@Document("carts")
@Getter
@Setter
public class Cart {

    @Id
    private String id;

    private List<CartItem> items = new ArrayList<>();

    private Instant updatedAt = Instant.now();

    @Getter
    @Setter
    public static class CartItem {
        private String productId;
        private int qty;

        public CartItem() { }

        public CartItem(String productId, int qty) {
            this.productId = productId;
            this.qty = qty;
        }
    }
}
