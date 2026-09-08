package com.attt.incident.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter.
 *
 * <p><strong>Security invariants enforced here:</strong>
 * <ol>
 *   <li>Only the JWT parsing / validation block is inside {@code try/catch}
 *       so that downstream exceptions (e.g., {@link IllegalArgumentException}
 *       from business logic) are never swallowed and misclassified as 401.</li>
 *   <li>Token version ({@code tv} claim) must equal the current
 *       {@link AppUserPrincipal#getTokenVersion()} value from the database.
 *       If not, the token is treated as revoked.</li>
 *   <li>Disabled accounts are rejected with a generic 401 — not 403 — so
 *       that the client cannot distinguish between "bad token" and "account
 *       disabled" (prevents account-state enumeration).</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        // ── JWT authentication block ─────────────────────────────────────
        // Only JWT-specific operations live inside this try/catch.
        // filterChain.doFilter() is intentionally OUTSIDE the try block.
        final AppUserPrincipal principal;
        try {
            final String username = jwtService.extractUsername(jwt);

            if (username == null || SecurityContextHolder.getContext().getAuthentication() != null) {
                // No username in token, or request is already authenticated — pass through.
                filterChain.doFilter(request, response);
                return;
            }

            principal = userDetailsService.loadUserByUsername(username);

            // ── Guard 1: account must be enabled ────────────────────────
            if (!principal.isEnabled()) {
                // Respond with 401 (not 403) to avoid leaking account state.
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(request, response,
                        new BadCredentialsException("Token không hợp lệ hoặc đã hết hạn"));
                return;
            }

            // ── Guard 2: token must not be expired / signature invalid ───
            if (!jwtService.isTokenValid(jwt, principal)) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(request, response,
                        new BadCredentialsException("Token không hợp lệ hoặc đã hết hạn"));
                return;
            }

            // ── Guard 3: token version must match current DB value ───────
            long claimTv  = jwtService.extractTokenVersion(jwt);
            long currentTv = principal.getTokenVersion();
            if (claimTv < currentTv) {
                // Token was issued before the last token-version increment
                // (e.g., password change, admin disable, explicit revocation).
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(request, response,
                        new BadCredentialsException("Token không hợp lệ hoặc đã hết hạn"));
                return;
            }

        } catch (JwtException | UsernameNotFoundException ex) {
            // JwtException: signature mismatch, expired, malformed, etc.
            // UsernameNotFoundException: username in token not found in DB.
            // Both conditions map to an unauthenticated request → 401.
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response,
                    new BadCredentialsException("Token không hợp lệ hoặc đã hết hạn", ex));
            return;
        }
        // ── End of JWT authentication block ─────────────────────────────

        // All guards passed — set authentication in context.
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // Proceed to the next filter / controller — outside the try/catch.
        filterChain.doFilter(request, response);
    }
}
