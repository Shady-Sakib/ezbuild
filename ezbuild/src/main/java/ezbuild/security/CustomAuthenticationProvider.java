package ezbuild.security;

import ezbuild.model.User;
import ezbuild.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Username or email + BCrypt, with suspended-account and lockout checks. Used by form login and POST
 * /api/auth/login.
 */
@Component
@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String login = authentication.getName() == null ? "" : authentication.getName().trim();
        String rawPassword = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();

        User user = userRepository.findByUsernameIgnoreCase(login)
                .or(() -> userRepository.findByEmailIgnoreCase(login))
                .orElseThrow(() -> new BadCredentialsException("Wrong username or password."));

        if (user.isLocked()) {
            throw new LockedException("Too many attempts. Try again in 15 minutes.");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            loginAttemptService.failed(user);
            throw new BadCredentialsException("Wrong username or password.");
        }
        if (user.isSuspended()) {
            throw new DisabledException("This account has been suspended.");
        }
        loginAttemptService.succeeded(user);
        return new UsernamePasswordAuthenticationToken(user.getUsername(), null, Authorities.of(user));
    }

    @Override
    public boolean supports(Class<?> authenticationType) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authenticationType);
    }
}
