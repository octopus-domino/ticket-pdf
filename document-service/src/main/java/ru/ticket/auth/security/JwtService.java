package ru.ticket.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import ru.ticket.auth.config.JwtProperties;
import ru.ticket.auth.domain.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        String secret = props.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret is not set. Add 'jwt.secret: <32+ chars>' to application.yml " +
                            "or set JWT_SECRET env variable."
            );
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret must be at least 32 bytes (got " + secretBytes.length + ")"
            );
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateAccessToken(User user) {
        return build(user, props.getAccessTokenTtlMs(), "access");
    }

    public String generateRefreshToken(User user) {
        return build(user, props.getRefreshTokenTtlMs(), "refresh");
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String build(User user, long ttlMs, String kind) {
        Date now = new Date();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(user.getId())
                .claim("role", user.getRole().name())
                .claim("kind", kind)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }
}