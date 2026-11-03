package com.quantumlock.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;
    private SecretKey signingKey;

    @PostConstruct
    void init() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(UserDetails user,
                                UUID tenantId,
                                List<String> roles,
                                String department,
                                String deviceTrustLevel) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtProperties.getExpirationMs());

        var builder = Jwts.builder()
                .subject(user.getUsername())
                .claim("tenant", tenantId.toString())
                .claim("roles", roles)
                .claim("department", department != null ? department : "")
                .claim("device_trust", deviceTrustLevel != null ? deviceTrustLevel : "STANDARD");

        if (user instanceof AppUserDetails appUser && appUser.getUserId() != null) {
            builder.claim("user_id", appUser.getUserId().toString());
        }

        return builder
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256);

        if (user instanceof AppUserDetails aud && aud.getUserId() != null) {
            builder.claim("uid", aud.getUserId().toString());
        }

        return builder.compact();
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new SecurityException("Invalid or expired JWT token: " + e.getMessage(), e);
        }
    }

    public Object extractClaim(Claims claims, String key) {
        return claims.get(key);
    }

    public <T> T extractClaim(Claims claims, String key, Class<T> clazz) {
        return claims.get(key, clazz);
    }
}
