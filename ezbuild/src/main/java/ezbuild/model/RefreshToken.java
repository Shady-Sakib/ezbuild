package ezbuild.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Stored only as a SHA-256 hash and rotated on every use. A TTL index removes expired ones. */
@Document("refresh_tokens")
@Getter
@Setter
public class RefreshToken {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed(unique = true)
    private String tokenHash;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    private boolean revoked;

    private String replacedBy;

    private Instant createdAt = Instant.now();
}
