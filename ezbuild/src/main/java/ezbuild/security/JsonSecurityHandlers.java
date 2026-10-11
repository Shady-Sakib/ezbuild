package ezbuild.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Instant;

/** 401/403 as the standard JSON error body for /api/**. */
public final class JsonSecurityHandlers {

    private JsonSecurityHandlers() { }

    public static AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> write(request, response, 401, "Unauthorized",
                request.getAttribute("jwt_error") != null ? "Invalid or expired token." : "Sign in first.");
    }

    public static AccessDeniedHandler accessDenied() {
        return (request, response, ex) -> write(request, response, 403, "Forbidden",
                "You do not have permission for this action.");
    }

    private static void write(HttpServletRequest request, HttpServletResponse response, int status, String error,
                              String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message
                + "\",\"path\":\"" + request.getRequestURI().replace("\"", "") + "\",\"timestamp\":\""
                + Instant.now() + "\"}");
    }
}
