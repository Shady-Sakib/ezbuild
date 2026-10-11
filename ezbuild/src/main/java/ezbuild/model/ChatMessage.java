package ezbuild.model;

import ezbuild.dto.BuildPlan;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Chat messages reference their session; build cards are embedded in the reply. */
@Document("chat_messages")
@CompoundIndex(name = "session_created", def = "{'sessionId': 1, 'createdAt': 1}")
@Getter
@Setter
public class ChatMessage {

    @Id
    private String id;

    private String sessionId;

    /** user or assistant */
    private String role;

    private String text;

    private List<BuildPlan> builds = new ArrayList<>();

    private List<String> suggestions = new ArrayList<>();

    private Instant createdAt = Instant.now();
}
