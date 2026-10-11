package ezbuildai;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Service-to-service auth: only callers with ADVISOR_KEY may use /v1/**. */
@Component
public class ServiceKeyFilter extends OncePerRequestFilter {

    @Value("${ezbuildai.service-key}")
    private String serviceKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/v1/")) {
            String given = request.getHeader("X-Service-Key");
            if (given == null || !MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8),
                    serviceKey.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Bad service key\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
