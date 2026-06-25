package com.linkup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Strongly-typed binding of the linkup.security.jwt.* config tree.
 *
 * Using @ConfigurationProperties (instead of scattered @Value strings) means the
 * config is validated and discoverable in one place, and the TTL is a real
 * Duration rather than a stringly-typed number.
 *
 * @param secret         base64-encoded 256-bit key for HS256 signing
 * @param accessTokenTtl how long an access token is valid
 * @param issuer         the "iss" claim we stamp and later verify
 */
@ConfigurationProperties(prefix = "linkup.security.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenTtl,
        String issuer
) {
}
