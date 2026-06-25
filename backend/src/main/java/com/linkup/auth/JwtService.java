package com.linkup.auth;

import com.linkup.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies stateless JWT access tokens (HS256).
 *
 * Design notes:
 *  - subject ("sub") = userId. The token is the identity; per-request auth is
 *    rebuilt from it without a DB lookup (stateless, scales horizontally — every
 *    WS/API pod can verify a token with just the shared secret).
 *  - Trade-off of stateless JWT: you cannot instantly revoke one before it expires.
 *    We keep the TTL short (1h) and would add a refresh-token + denylist later if
 *    instant revocation becomes a requirement. This is a classic interview tension.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        this.props = props;
        // Decode the base64 secret into raw bytes, then build an HMAC-SHA key.
        // Keys.hmacShaKeyFor enforces a minimum key length for the algorithm.
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.secret()));
    }

    /** Mint a signed access token for an authenticated principal. */
    public String generateToken(AppUserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(props.accessTokenTtl());
        return Jwts.builder()
                .issuer(props.issuer())
                .subject(principal.getId().toString())
                .claim("username", principal.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return props.accessTokenTtl().toSeconds();
    }

    /**
     * Verify signature + expiry + issuer and return the principal, or throw
     * JwtException if anything is off (bad signature, expired, wrong issuer).
     */
    public AppUserPrincipal parse(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID userId = UUID.fromString(claims.getSubject());
        String username = claims.get("username", String.class);
        return AppUserPrincipal.fromClaims(userId, username);
    }
}
