package ezbuild.repository;

import ezbuild.model.ChatSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends MongoRepository<ChatSession, String> {

    List<ChatSession> findTop20ByUserIdOrderByLastActiveAtDesc(String userId);

    Optional<ChatSession> findByIdAndUserId(String id, String userId);
}
