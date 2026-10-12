package ezbuild.security;

import ezbuild.config.AppProperties;
import ezbuild.model.Role;
import ezbuild.model.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef-test".getBytes());
    private JwtService jwt;
    private User user;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("http://localhost", "uploads", false, false, "x", "k", "", false, "a@b.c",
                new AppProperties.Jwt(SECRET, 15, 7), new AppProperties.Shipping(60, 100),
                new AppProperties.Payment(50, 30), new AppProperties.SslCommerz("", "", "", 500000),
                new AppProperties.Stripe("", "", 122), new AppProperties.Advisor("local", "", ""));
        jwt = new JwtService(props);
        user = new User();
        user.setId("u1");
        user.setUsername("rahim");
        user.setRoles(EnumSet.of(Role.ROLE_STAFF, Role.ROLE_USER));
    }

    @Test
    void roundTrip() {
        var claims = jwt.parse(jwt.issue(user));
        assertThat(claims.getSubject()).isEqualTo("u1");
        assertThat(claims.get("authorities", java.util.List.class)).contains("PRODUCT_WRITE");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwt.issue(user);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThatThrownBy(() -> jwt.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void wrongAudienceIsRejected() {
        String other = Jwts.builder().subject("u1").issuer(JwtService.ISSUER).audience().add("someone-else").and()
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)), Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> jwt.parse(other)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        String old = Jwts.builder().subject("u1").issuer(JwtService.ISSUER).audience().add(JwtService.AUDIENCE).and()
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)), Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> jwt.parse(old)).isInstanceOf(JwtException.class);
    }

    @Test
    void unsignedTokenIsRejected() {
        String none = Jwts.builder().subject("u1").issuer(JwtService.ISSUER).audience().add(JwtService.AUDIENCE).and().compact();
        assertThatThrownBy(() -> jwt.parse(none)).isInstanceOf(JwtException.class);
    }
}
