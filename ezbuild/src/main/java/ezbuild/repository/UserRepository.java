package ezbuild.repository;

import ezbuild.model.Role;
import ezbuild.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    long countByRolesContaining(Role role);

    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email,
                                                                               Pageable pageable);
}
