package ezbuild.controller;

import ezbuild.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Session -> JWT bridge for page JavaScript. Lives in the page chain, so it needs the session and a CSRF
 * token. The JS keeps the short-lived token in memory only.
 */
@RestController
@RequiredArgsConstructor
public class TokenBridgeController {

    private final JwtService jwtService;
    private final CurrentUser currentUser;

    @PostMapping("/auth/token")
    public Map<String, Object> token(Authentication authentication) {
        var me = currentUser.require(authentication);
        return Map.of("accessToken", jwtService.issue(me), "expiresIn", jwtService.accessSeconds());
    }
}
