package com.attt.incident.security;

import com.attt.incident.entity.Role;
import com.attt.incident.entity.RoleName;
import com.attt.incident.entity.User;
import com.attt.incident.repository.RefreshTokenRepository;
import com.attt.incident.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent admin bootstrap runner.
 *
 * <p>Runs after Flyway migrations on every startup. Only performs work when
 * {@code APP_BOOTSTRAP_ADMIN_ENABLED=true} is explicitly set.
 *
 * <h3>Behaviour matrix</h3>
 * <table border="1">
 *   <tr><th>Scenario</th><th>Action</th></tr>
 *   <tr><td>Bootstrap disabled (default)</td><td>No-op — silent skip</td></tr>
 *   <tr><td>Admin not found in DB</td><td>Warn + skip (seed migration problem)</td></tr>
 *   <tr><td>Admin already enabled</td><td>No-op — idempotent skip</td></tr>
 *   <tr><td>Admin disabled (fresh DB / V6 applied)</td><td>Re-enable + set password from env</td></tr>
 * </table>
 *
 * <h3>Security contract</h3>
 * <ul>
 *   <li>Password is read from environment variable only — never logged.</li>
 *   <li>{@code token_version} is incremented and refresh tokens are revoked
 *       so every session issued before bootstrap is invalid.</li>
 *   <li>If admin is already enabled, this runner never touches any field.</li>
 *   <li>Production must NOT set {@code APP_BOOTSTRAP_ADMIN_ENABLED=true} after
 *       initial setup — disable it after first successful login.</li>
 * </ul>
 */
@Slf4j
@Component
@Order(10)  // run after Flyway (Order 0) but early in application startup
@RequiredArgsConstructor
public class AdminBootstrapRunner implements CommandLineRunner {

    @Value("${app.bootstrap.admin.enabled:false}")
    private boolean bootstrapEnabled;

    @Value("${app.bootstrap.admin.username:admin}")
    private String adminUsername;

    @Value("${app.bootstrap.admin.password:}")
    private String adminPassword;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (!bootstrapEnabled) {
            // Silent skip — this is the normal production path.
            return;
        }

        log.info("=== Admin Bootstrap Runner active (APP_BOOTSTRAP_ADMIN_ENABLED=true) ===");

        // ── Validate password from env ────────────────────────────────
        if (adminPassword == null || adminPassword.isBlank()) {
            log.error("Bootstrap SKIPPED: APP_BOOTSTRAP_ADMIN_PASSWORD is not set or blank. " +
                      "Set the environment variable and restart.");
            return;
        }

        String policyError = PasswordPolicy.validate(adminPassword);
        if (policyError != null) {
            log.error("Bootstrap SKIPPED: APP_BOOTSTRAP_ADMIN_PASSWORD does not meet policy: {}", policyError);
            return;
        }

        // ── Locate admin user ─────────────────────────────────────────
        User admin = userRepository.findByUsername(adminUsername).orElse(null);
        if (admin == null) {
            log.warn("Bootstrap SKIPPED: user '{}' not found. " +
                     "Ensure seed migration (V2) has run successfully.", adminUsername);
            return;
        }

        // ── Idempotency guard: already enabled → nothing to do ────────
        if (admin.isEnabled()) {
            log.info("Bootstrap skipped: user '{}' is already enabled. No changes made.", adminUsername);
            return;
        }

        // ── Verify admin role before granting access ──────────────────
        boolean hasAdminRole = admin.getRoles().stream()
                .anyMatch(r -> r.getName() == RoleName.ADMIN);
        if (!hasAdminRole) {
            log.error("Bootstrap SKIPPED: user '{}' does not have the ADMIN role. " +
                      "Manual intervention required.", adminUsername);
            return;
        }

        // ── Re-enable admin + set bootstrap password ──────────────────
        admin.setEnabled(true);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setFailedLoginAttempts(0);
        admin.setAccountLockedUntil(null);
        admin.setTokenVersion(admin.getTokenVersion() + 1);
        admin.setPasswordChangedAt(java.time.LocalDateTime.now());
        userRepository.save(admin);

        // Clean up any stale refresh tokens from before the account was disabled.
        int deleted = refreshTokenRepository.deleteByUser(admin);
        if (deleted > 0) {
            log.info("Bootstrap: {} stale refresh token(s) removed for '{}'.", deleted, adminUsername);
        }

        log.warn("=================================================================");
        log.warn("Bootstrap SUCCESS: user '{}' has been enabled.", adminUsername);
        log.warn("  • Password set from APP_BOOTSTRAP_ADMIN_PASSWORD (BCrypt hashed)");
        log.warn("  • failed_login_attempts reset to 0");
        log.warn("  • account_locked_until cleared");
        log.warn("  • token_version incremented and previous sessions revoked");
        log.warn("IMPORTANT: Disable bootstrap after first successful login:");
        log.warn("  Remove APP_BOOTSTRAP_ADMIN_ENABLED=true from your environment.");
        log.warn("=================================================================");
    }
}
