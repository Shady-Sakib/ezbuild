package ezbuild.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("password_reset_tokens")
@Getter
@Setter
public class PasswordResetToken {

    @Id
    private String id;

    private String userId;

    @Indexed(unique = true)
    private String tokenHash;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    private boolean used;
}
