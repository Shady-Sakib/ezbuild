package ezbuild.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request/response records for /api/auth/**. */
public final class AuthDtos {

    private AuthDtos() { }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "^[A-Za-z0-9_]+$") String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.") String password) { }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) { }

    public record RefreshRequest(@NotBlank String refreshToken) { }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) { }

    public record MeResponse(String id, String username, String email, List<String> roles,
                             List<String> authorities) { }
}
