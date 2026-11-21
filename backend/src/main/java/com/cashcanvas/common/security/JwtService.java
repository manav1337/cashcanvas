package com.cashcanvas.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and verifies HS256 access tokens. Subject = user id; one "email" claim.
 * Verification is stateless (no DB hit per request). Trade-off: a deleted user's token
 * stays valid until it expires, which is why the expiry is short (1h).
 */
@Service
public class JwtService {

    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;
    private final Duration expiry;
    private final String issuer;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException("cashcanvas.jwt.secret (env JWT_SECRET) must be set to a base64 key");
        }
        byte[] keyBytes = Decoders.BASE64.decode(properties.secret());
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("cashcanvas.jwt.secret must decode to at least " + MIN_KEY_BYTES + " bytes");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expiry = properties.expiry();
        this.issuer = properties.issuer();
        this.clock = clock;
    }

    public IssuedToken issue(Long userId, String email) {
        Instant now = clock.instant();
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiry)))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiry.toSeconds());
    }

    /**
     * @throws JwtException             if the signature, issuer or expiry is invalid
     * @throws IllegalArgumentException if the token is blank or the subject is not a number
     */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthenticatedUser(Long.valueOf(claims.getSubject()), claims.get("email", String.class));
    }
}
