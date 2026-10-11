package ezbuild.repository;

import ezbuild.model.Build;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BuildRepository extends MongoRepository<Build, String> {

    List<Build> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Build> findByIdAndUserId(String id, String userId);

    Optional<Build> findByShareCode(String shareCode);
}
