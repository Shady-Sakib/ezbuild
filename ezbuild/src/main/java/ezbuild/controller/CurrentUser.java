package ezbuild.controller;

import ezbuild.model.User;
import ezbuild.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Authentication -> our User document (null for guests). */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserService userService;

    public User from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return userService.findByUsername(authentication.getName());
    }

    /** For endpoints already protected by the filter chain. */
    public User require(Authentication authentication) {
        User user = from(authentication);
        if (user == null) throw new org.springframework.security.access.AccessDeniedException("Sign in first.");
        return user;
    }
}
