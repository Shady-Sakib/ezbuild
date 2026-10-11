package ezbuild.service;

import ezbuild.config.AppProperties;
import ezbuild.model.PasswordResetToken;
import ezbuild.model.User;
import ezbuild.repository.PasswordResetTokenRepository;
import ezbuild.repository.UserRepository;
import ezbuild.util.Hashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Single-use, hashed, 30-minute reset tokens (cleaned up by a TTL index). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final UserService userService;
    private final MailService mailService;
    private final AppProperties props;

    /** Behaves the same whether or not the email exists (no account enumeration). */
    public void request(String email) {
        userRepository.findByEmailIgnoreCase(email == null ? "" : email.trim()).ifPresent(user -> {
            String raw = Hashing.randomToken(32);
            PasswordResetToken t = new PasswordResetToken();
            t.setUserId(user.getId());
            t.setTokenHash(Hashing.sha256(raw));
            t.setExpiresAt(Instant.now().plus(Duration.ofMinutes(30)));
            tokenRepository.save(t);
            mailService.send(user.getEmail(), "Reset your EZBuild password",
                    "Use this link within 30 minutes to choose a new password:\n" + props.baseUrl()
                            + "/reset-password?token=" + raw + "\n\nIf you did not ask for this, ignore this email.");
        });
    }

    public boolean valid(String rawToken) {
        return find(rawToken) != null;
    }

    public void reset(String rawToken, String newPassword) {
        PasswordResetToken t = find(rawToken);
        if (t == null) throw new BusinessException("This reset link is invalid or has expired.");
        User user = userService.get(t.getUserId());
        userService.setPassword(user, newPassword);
        t.setUsed(true);
        tokenRepository.save(t);
        log.info("Password reset completed for user={}", user.getUsername());
    }

    private PasswordResetToken find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        return tokenRepository.findByTokenHash(Hashing.sha256(rawToken))
                .filter(t -> !t.isUsed() && t.getExpiresAt().isAfter(Instant.now()))
                .orElse(null);
    }
}
