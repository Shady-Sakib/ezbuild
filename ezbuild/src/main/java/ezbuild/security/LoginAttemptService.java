package ezbuild.security;

import ezbuild.model.User;
import ezbuild.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Five wrong passwords lock the account for 15 minutes (stored on the user). */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    public static final int MAX_FAILURES = 5;
    public static final Duration LOCK_TIME = Duration.ofMinutes(15);

    private final UserRepository userRepository;

    public void failed(User user) {
        user.setFailedLogins(user.getFailedLogins() + 1);
        if (user.getFailedLogins() >= MAX_FAILURES) {
            user.setLockedUntil(Instant.now().plus(LOCK_TIME));
            user.setFailedLogins(0);
            log.warn("Account locked for 15 minutes after repeated failures: user={}", user.getUsername());
        }
        userRepository.save(user);
    }

    public void succeeded(User user) {
        if (user.getFailedLogins() > 0 || user.getLockedUntil() != null) {
            user.setFailedLogins(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }
}
