package ezbuild.service;

import ezbuild.config.AppProperties;
import ezbuild.dto.AuthDtos;
import ezbuild.model.RefreshToken;
import ezbuild.model.User;
import ezbuild.repository.RefreshTokenRepository;
import ezbuild.repository.UserRepository;
import ezbuild.security.CustomAuthenticationProvider;
import ezbuild.security.JwtService;
import ezbuild.util.Hashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * API auth lifecycle: 15-min access token + 7-day opaque refresh token, stored only as a SHA-256 hash and
 * rotated on every use. Re-using an old refresh token revokes them all.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CustomAuthenticationProvider authenticationProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final AppProperties props;

    public AuthDtos.TokenResponse login(String username, String password) {
        Authentication auth = authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
        User user = userRepository.findByUsernameIgnoreCase(auth.getName()).orElseThrow();
        return issue(user);
    }

    public AuthDtos.TokenResponse issue(User user) {
        String raw = Hashing.randomToken(32);
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(Hashing.sha256(raw));
        rt.setExpiresAt(Instant.now().plus(Duration.ofDays(props.jwt().refreshDays())));
        refreshTokenRepository.save(rt);
        return new AuthDtos.TokenResponse(jwtService.issue(user), raw, "Bearer", jwtService.accessSeconds());
    }

    public AuthDtos.TokenResponse refresh(String rawRefresh) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(Hashing.sha256(rawRefresh))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token."));
        if (current.isRevoked()) {
            // an already-rotated token came back: it may be stolen, so revoke the whole family
            revokeAll(current.getUserId());
            log.warn("Refresh token reuse detected for userId={}: all tokens revoked", current.getUserId());
            throw new BadCredentialsException("Refresh token was already used. Please sign in again.");
        }
        if (current.getExpiresAt().isBefore(Instant.now())) throw new BadCredentialsException("Refresh token expired.");
        User user = userRepository.findById(current.getUserId())
                .filter(u -> !u.isSuspended())
                .orElseThrow(() -> new BadCredentialsException("Account unavailable."));
        AuthDtos.TokenResponse next = issue(user);
        current.setRevoked(true);
        current.setReplacedBy(Hashing.sha256(next.refreshToken()));
        refreshTokenRepository.save(current);
        return next;
    }

    public void logout(String rawRefresh) {
        if (rawRefresh == null) return;
        refreshTokenRepository.findByTokenHash(Hashing.sha256(rawRefresh)).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }

    public void revokeAll(String userId) {
        var tokens = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
        tokens.forEach(t -> t.setRevoked(true));
        refreshTokenRepository.saveAll(tokens);
    }
}
