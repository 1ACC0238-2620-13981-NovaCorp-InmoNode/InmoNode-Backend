package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.tokens.jwt.services;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenClaims;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates HS256 JSON Web Tokens (US-01, US-31).
 */
@Service
public class JwtTokenService implements TokenService {

    private static final String EMAIL_CLAIM = "email";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final long expirationSeconds;
    private final Clock clock;

    public JwtTokenService(@Value("${authorization.jwt.secret}") String secret,
                           @Value("${authorization.jwt.expiration-seconds:3600}") long expirationSeconds,
                           Clock clock) {
        var bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("authorization.jwt.secret must be at least 32 bytes long");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationSeconds = expirationSeconds;
        this.clock = clock;
    }

    @Override
    public String generateToken(User user) {
        var issuedAt = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(EMAIL_CLAIM, user.getEmail())
                .claim(ROLE_CLAIM, user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(expirationSeconds)))
                .signWith(key)
                .compact();
    }

    @Override
    public long expirationSeconds() {
        return expirationSeconds;
    }

    @Override
    public Optional<TokenClaims> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new TokenClaims(
                    Long.valueOf(claims.getSubject()),
                    claims.get(EMAIL_CLAIM, String.class),
                    Role.valueOf(claims.get(ROLE_CLAIM, String.class))));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
