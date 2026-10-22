package cd.moziki.security;

import cd.moziki.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long ttlMinutes;

    public JwtService(JwtProperties properties) {
        byte[] secret = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "MOZIKI_JWT_SECRET must be at least 32 bytes for HS256 (openssl rand -base64 48)");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret);
        this.ttlMinutes = properties.ttlMinutes();
    }

    public String issue(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.getId().toString())
                .claim("coop", principal.getCooperativeId().toString())
                .claim("role", principal.getRole().name())
                .claim("name", principal.getFullName())
                .claim("locale", principal.getLocale())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlMinutes * 60)))
                .signWith(signingKey)
                .compact();
    }

    public UUID parseUserId(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }

    public long getTtlMinutes() {
        return ttlMinutes;
    }
}
