package ezbuild.config;

import ezbuild.repository.UserRepository;
import ezbuild.security.CustomAuthenticationProvider;
import ezbuild.security.CustomUserDetailsService;
import ezbuild.security.JsonSecurityHandlers;
import ezbuild.security.JwtAuthFilter;
import ezbuild.security.JwtService;
import ezbuild.security.RateLimitFilter;
import ezbuild.security.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Two filter chains. /api/**: stateless JWT, no CSRF, JSON 401/403, CORS allow-list. Pages: form login,
 * session, CSRF, remember-me. Both use BCrypt, the custom provider, role + authority rules, and @PreAuthorize
 * via method security.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String CSP = "default-src 'self'; "
            + "img-src 'self' data: https:; "
            + "script-src 'self'; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
            + "font-src 'self' https://fonts.gstatic.com; "
            + "connect-src 'self'; "
            + "frame-ancestors 'none'; "
            + "form-action 'self' https://sandbox.sslcommerz.com https://securepay.sslcommerz.com "
            + "https://checkout.stripe.com; "
            + "base-uri 'self'";

    private final CustomAuthenticationProvider authenticationProvider;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RateLimiter rateLimiter;
    private final AppProperties props;

    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .authenticationProvider(authenticationProvider)
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**", "/api/categories",
                        "/api/brands", "/api/facets", "/api/builds/shared/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/builds/validate").permitAll()
                .requestMatchers("/api/payments/sslcommerz/ipn", "/api/payments/stripe/webhook").permitAll()
                .requestMatchers("/api/admin/products/**").hasAuthority("PRODUCT_WRITE")
                .requestMatchers("/api/admin/orders/*/refund").hasAuthority("ORDER_REFUND")
                .requestMatchers("/api/admin/orders/**").hasAuthority("ORDER_MANAGE")
                .requestMatchers("/api/admin/stats").hasAuthority("VIEW_REPORTS")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint(JsonSecurityHandlers.entryPoint())
                .accessDeniedHandler(JsonSecurityHandlers.accessDenied())
            )
            .addFilterBefore(new RateLimitFilter(rateLimiter), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtAuthFilter(jwtService, userRepository), UsernamePasswordAuthenticationFilter.class)
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authenticationProvider)
            // SSLCommerz posts the browser back from its own site, so there's no CSRF token;
            // the handler trusts nothing in that POST and verifies through the Validation API instead
            .csrf(csrf -> csrf.ignoringRequestMatchers("/payment/sslcommerz/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/products", "/products/**", "/builder", "/b/**", "/compare",
                        "/sign-in", "/sign-up", "/forgot-password", "/reset-password", "/privacy", "/support",
                        "/track", "/error", "/payment/sslcommerz/**").permitAll()
                .requestMatchers("/css/**", "/js/**", "/media/**", "/favicon.ico", "/favicon.svg").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/admin/products/**").hasAuthority("PRODUCT_WRITE")
                .requestMatchers("/admin/orders/*/refund").hasAuthority("ORDER_REFUND")
                .requestMatchers("/admin/orders/**", "/admin/support/**").hasAuthority("ORDER_MANAGE")
                .requestMatchers("/admin/members/**").hasRole("ADMIN")
                .requestMatchers("/admin", "/admin/").hasAuthority("VIEW_REPORTS")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/sign-in")
                .loginProcessingUrl("/sign-in")
                .usernameParameter("username")
                .passwordParameter("password")
                .defaultSuccessUrl("/", false)
                .failureHandler((request, response, ex) -> {
                    String reason = ex instanceof LockedException ? "locked"
                            : ex instanceof DisabledException ? "suspended" : "bad";
                    response.sendRedirect(request.getContextPath() + "/sign-in?error=" + reason);
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/sign-in?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "remember-me")
                .permitAll()
            )
            .rememberMe(remember -> remember
                .key(props.rememberMeKey())
                .rememberMeParameter("remember-me")
                .userDetailsService(userDetailsService)
                .tokenValiditySeconds(7 * 24 * 3600)
            )
            .sessionManagement(s -> s.sessionFixation(f -> f.migrateSession()))
            .addFilterBefore(new RateLimitFilter(rateLimiter), UsernamePasswordAuthenticationFilter.class)
            .headers(h -> h
                .contentSecurityPolicy(c -> c.policyDirectives(CSP))
                .frameOptions(f -> f.deny())
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            );
        return http.build();
    }

    private CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        String origins = props.corsOrigins();
        if (origins != null && !origins.isBlank()) {
            config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        } else {
            config.setAllowedOrigins(List.of(props.baseUrl()));
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
