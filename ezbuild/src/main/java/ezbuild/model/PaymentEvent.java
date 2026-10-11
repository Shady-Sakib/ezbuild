package ezbuild.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** _id = provider event key; a duplicate insert fails, which makes webhooks idempotent. */
@Document("payment_events")
@Getter
@Setter
@NoArgsConstructor
public class PaymentEvent {

    @Id
    private String id;

    private String provider;

    private String type;

    private Instant receivedAt = Instant.now();

    public PaymentEvent(String id, String provider, String type) {
        this.id = id;
        this.provider = provider;
        this.type = type;
    }
}
