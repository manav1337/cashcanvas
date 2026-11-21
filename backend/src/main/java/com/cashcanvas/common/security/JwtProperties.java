package com.cashcanvas.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param secret base64-encoded HMAC key, at least 32 bytes after decoding. Comes from JWT_SECRET in prod.
 */
@ConfigurationProperties("cashcanvas.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("1h") Duration expiry,
        @DefaultValue("cashcanvas") String issuer) {
}
