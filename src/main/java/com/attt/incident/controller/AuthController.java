package com.attt.incident.controller;

import com.attt.incident.dto.AuthResponse;
import com.attt.incident.dto.LoginRequest;
import com.attt.incident.security.AppUserPrincipal;
import com.attt.incident.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.attt.incident.entity.User;
import com.attt.incident.repository.UserRepository;
import com.attt.incident.security.AppUserDetailsService;
import com.attt.incident.security.LoginAttemptService;
import com.attt.incident.security.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Authentication endpoints.
 *
 * <p><strong>Security design:</strong>
 * <ul>
 *   <li>All authentication failures (wrong password, disabled account, locked
 *       account, unknown username) return HTTP 401 with an identical generic
 *       message to prevent username / account-state enumeration.</li>
 *   <li>Refresh token failures also return HTTP 401 with a generic message.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String GENERIC_AUTH_ERROR = "Tên đăng nhập hoặc mật khẩu không đúng.";
    private static final String GENERIC_REFRESH_ERROR = "Token không hợp lệ hoặc đã hết hạn.";
    private static final String REFRESH_COOKIE = "incident_refresh";

    @Value("${app.auth.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    @Value("${app.auth.refresh-cookie-same-site:Strict}")
    private String refreshCookieSameSite;

    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    // ── POST /api/auth/login ─────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        String ipAddress = httpRequest.getRemoteAddr();

        if (loginAttemptService.isIpBlocked(ipAddress)
                || loginAttemptService.isAccountLocked(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorBody(GENERIC_AUTH_ERROR));
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(), request.getPassword()));
        } catch (BadCredentialsException | DisabledException | LockedException ex) {
            // All three conditions return the same generic 401 message so
            // that the client cannot determine *why* authentication failed.
            loginAttemptService.loginFailed(request.getUsername(), ipAddress);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorBody(GENERIC_AUTH_ERROR));
        } catch (AuthenticationException ex) {
            // Covers UsernameNotFoundException and any other Spring Security
            // authentication failure — same generic response.
            loginAttemptService.loginFailed(request.getUsername(), ipAddress);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorBody(GENERIC_AUTH_ERROR));
        }

        // Authentication succeeded.
        loginAttemptService.loginSucceeded(request.getUsername(), ipAddress);

        AppUserPrincipal principal =
                (AppUserPrincipal) userDetailsService.loadUserByUsername(request.getUsername());
        String accessToken = jwtService.generateToken(principal);

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException(GENERIC_AUTH_ERROR));

        String refreshToken = refreshTokenService.createRefreshToken(user.getId());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken).toString());
        httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        List<String> roles = principal.getAuthorities().stream()
                .map(Object::toString)
                .toList();

        return ResponseEntity.ok(
                new AuthResponse(accessToken, principal.getUsername(), roles));
    }

    // ── POST /api/auth/refresh ───────────────────────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            RefreshTokenService.RotatedRefreshToken rotated =
                    refreshTokenService.rotateRefreshToken(refreshToken);
            User user = rotated.user();
            AppUserPrincipal principal = userDetailsService.loadUserByUsername(user.getUsername());
            String accessToken = jwtService.generateToken(principal);
            List<String> roles = principal.getAuthorities().stream().map(Object::toString).toList();
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(rotated.token()).toString());
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            return ResponseEntity.ok(new AuthResponse(accessToken, user.getUsername(), roles));
        } catch (com.attt.incident.security.InvalidRefreshTokenException ex) {
            response.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorBody(GENERIC_REFRESH_ERROR));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        refreshTokenService.revokeToken(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString());
        return ResponseEntity.noContent().build();
    }

    private ResponseCookie refreshCookie(String token) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/auth")
                .maxAge(java.time.Duration.ofMillis(refreshExpirationMs))
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/auth")
                .maxAge(java.time.Duration.ZERO)
                .build();
    }

    // ── Internal error response body ────────────────────────────────

    record ErrorBody(String message) {}
}
