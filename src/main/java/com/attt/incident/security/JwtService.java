package com.attt.incident.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stateless JWT service.
 *
 * <p>Every token carries the following claims beyond the JJWT defaults:
 * <ul>
 *   <li>{@code sub} – username (standard claim)</li>
 *   <li>{@code roles} – list of ROLE_* strings</li>
 *   <li>{@code tv} – token_version at issuance time (per-user invalidation)</li>
 * </ul>
 *
 * <p>{@link JwtAuthFilter} extracts the {@code tv} claim and rejects the token
 * if the current value in the database is greater, i.e., the user's token
 * version has been incremented since this token was issued.
 */
@Service
public class JwtService {

    /** JWT claim name for token version. Short to keep token compact. */
    public static final String CLAIM_TOKEN_VERSION = "tv";

    @Value("${app.jwt.secret:change-this-secret-key-in-production-min-256-bits-long}")
    private String secret;

    @Value("${app.jwt.expiration-ms:86400000}") // default 24 h
    private long expirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * Generates a signed JWT for the given principal.
     *
     * <p>Requires {@code userDetails} to be an instance of {@link AppUserPrincipal}
     * so that the {@code token_version} can be embedded. If a plain
     * {@link UserDetails} is passed (e.g., from legacy test code), the token
     * version defaults to {@code 0} and version-based invalidation will not work.
     */
    public String generateToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toList());

        long tokenVersion = 0L;
        if (userDetails instanceof AppUserPrincipal principal) {
            tokenVersion = principal.getTokenVersion();
        }

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("roles", roles)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the {@code tv} claim from the token.
     * Returns {@code 0L} if the claim is absent (tokens issued before V6).
     */
    public long extractTokenVersion(String token) {
        Claims claims = extractAllClaims(token);
        Number tv = claims.get(CLAIM_TOKEN_VERSION, Number.class);
        return tv != null ? tv.longValue() : 0L;
    }

    /**
     * Validates that:
     * <ol>
     *   <li>The subject matches {@code userDetails.username}.</li>
     *   <li>The token has not expired.</li>
     *   <li>The embedded {@code tv} claim equals the principal's current
     *       {@link AppUserPrincipal#getTokenVersion()} — enforced by the
     *       filter, not here, to keep this method side-effect-free.</li>
     * </ol>
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
