package ezbuild.repository;

import ezbuild.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<ChatMessage> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    void deleteBySessionId(String sessionId);
}
