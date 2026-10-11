package ezbuild.api;

import ezbuild.controller.CurrentUser;
import ezbuild.dto.AuthDtos;
import ezbuild.model.User;
import ezbuild.security.Authorities;
import ezbuild.service.AuthService;
import ezbuild.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login / refresh / logout / me for API clients (Postman, mobile). */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthService authService;
    private final UserService userService;
    private final CurrentUser currentUser;

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.TokenResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest body) {
        User user = userService.register(body.username(), body.email(), body.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.issue(user));
    }

    @PostMapping("/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest body) {
        return authService.login(body.username(), body.password());
    }

    @PostMapping("/refresh")
    public AuthDtos.TokenResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest body) {
        return authService.refresh(body.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) AuthDtos.RefreshRequest body) {
        authService.logout(body == null ? null : body.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthDtos.MeResponse me(Authentication authentication) {
        User me = currentUser.require(authentication);
        return new AuthDtos.MeResponse(me.getId(), me.getUsername(), me.getEmail(),
                me.getRoles().stream().map(Enum::name).toList(),
                Authorities.authoritySet(me).stream().map(Enum::name).toList());
    }
}
