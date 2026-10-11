package ezbuild.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("chat_sessions")
@CompoundIndex(name = "user_active", def = "{'userId': 1, 'lastActiveAt': -1}")
@Getter
@Setter
public class ChatSession {

    @Id
    private String id;

    private String userId;

    private String title = "New chat";

    private Instant createdAt = Instant.now();

    private Instant lastActiveAt = Instant.now();

    /** Last build request in this chat, so follow-ups keep the budget and use case. */
    private ezbuild.dto.BuildRequirements lastRequirements;
}
