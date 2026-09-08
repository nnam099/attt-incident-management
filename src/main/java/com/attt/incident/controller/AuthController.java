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

import com.attt.incident.dto.RefreshTokenRequest;
import com.attt.incident.entity.RefreshToken;
import com.attt.incident.entity.User;
import com.attt.incident.repository.UserRepository;
import com.attt.incident.security.AppUserDetailsService;
import com.attt.incident.security.LoginAttemptService;
import com.attt.incident.security.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.AuthenticationException;
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
 *   <li>Only the IP-lock check is surfaced to the client (HTTP 429-style 401)
 *       because the client already knows it has been locked (it caused it).</li>
 *   <li>Refresh token failures also return HTTP 401 with a generic message.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String GENERIC_AUTH_ERROR = "Tên đăng nhập hoặc mật khẩu không đúng.";
    private static final String LOCKED_ERROR =
            "Tài khoản của bạn đã bị khóa tạm thời do nhập sai quá nhiều lần. Vui lòng thử lại sau.";
    private static final String GENERIC_REFRESH_ERROR = "Token không hợp lệ hoặc đã hết hạn.";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    // ── POST /api/auth/login ─────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();

        // IP/account lock check — surfaced to client (client caused it).
        if (loginAttemptService.isAccountLocked(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorBody(LOCKED_ERROR));
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

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        List<String> roles = principal.getAuthorities().stream()
                .map(Object::toString)
                .toList();

        return ResponseEntity.ok(
                new AuthResponse(accessToken, refreshToken.getToken(), principal.getUsername(), roles));
    }

    // ── POST /api/auth/refresh ───────────────────────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return refreshTokenService.findByToken(request.getRefreshToken())
                .map(rt -> {
                    try {
                        return refreshTokenService.verifyExpiration(rt);
                    } catch (RuntimeException ex) {
                        // Expired or invalid refresh token — generic 401.
                        return null;
                    }
                })
                .map(rt -> {
                    if (rt == null) return null;
                    User user = rt.getUser();
                    // Reject if user is now disabled — prevents re-auth via
                    // an old refresh token after the account is disabled.
                    if (!user.isEnabled()) return null;

                    AppUserPrincipal principal =
                            (AppUserPrincipal) userDetailsService.loadUserByUsername(user.getUsername());
                    String accessToken = jwtService.generateToken(principal);
                    List<String> roles = principal.getAuthorities().stream()
                            .map(Object::toString)
                            .toList();
                    return (Object) ResponseEntity.ok(
                            new AuthResponse(accessToken, request.getRefreshToken(),
                                    user.getUsername(), roles));
                })
                .map(obj -> obj == null
                        ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorBody(GENERIC_REFRESH_ERROR))
                        : (ResponseEntity<?>) obj)
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorBody(GENERIC_REFRESH_ERROR)));
    }

    // ── Internal error response body ────────────────────────────────

    record ErrorBody(String message) {}
}
