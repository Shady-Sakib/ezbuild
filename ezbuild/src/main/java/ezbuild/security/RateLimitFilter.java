package ezbuild.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/** Per-IP limits for login/sign-up, chat and checkout POSTs. Answers 429. */
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String path = request.getRequestURI();
            String ip = request.getRemoteAddr();
            int limit = 0;
            String bucket = null;
            if (path.startsWith("/api/auth/") || path.equals("/sign-in") || path.equals("/sign-up")
                    || path.equals("/forgot-password")) {
                bucket = "auth";
                limit = 10;
            } else if (path.startsWith("/api/chat")) {
                bucket = "chat";
                limit = 10;
            } else if (path.equals("/checkout") || path.equals("/api/orders")) {
                bucket = "checkout";
                limit = 5;
            }
            if (bucket != null && !limiter.tryAcquire(bucket + ":" + ip, limit, Duration.ofMinutes(1))) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\","
                        + "\"message\":\"Slow down a little and try again in a minute.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
