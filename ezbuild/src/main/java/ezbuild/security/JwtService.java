package ezbuild.security;

import ezbuild.config.AppProperties;
import ezbuild.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * HS256 access tokens (15 min). Claims: sub = user id, username, roles, authorities, iss, aud, iat, exp, jti.
 * Never put personal data in here: the payload is only encoded, not encrypted.
 */
@Service
public class JwtService {

    public static final String ISSUER = "ezbuild";
    public static final String AUDIENCE = "ezbuild-api";

    private final SecretKey key;
    private final long accessSeconds;

    public JwtService(AppProperties props) {
        byte[] bytes = Decoders.BASE64.decode(props.jwt().secret());
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be base64 of at least 32 random bytes.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessSeconds = props.jwt().accessMinutes() * 60L;
    }

    public long accessSeconds() {
        return accessSeconds;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("username", user.getUsername())
                .claim("roles", user.getRoles().stream().map(Enum::name).toList())
                .claim("authorities", Authorities.authoritySet(user).stream().map(Enum::name).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Checks signature, expiry, issuer and audience, and pins the algorithm to HS256. */
    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build()
                .parseSignedClaims(token);
        if (!"HS256".equals(jws.getHeader().getAlgorithm())) {
            throw new JwtException("Unexpected algorithm");
        }
        return jws.getPayload();
    }
}
