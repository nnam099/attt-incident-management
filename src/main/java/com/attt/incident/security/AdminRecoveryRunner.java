package com.attt.incident.security;

import com.attt.incident.entity.RoleName;
import com.attt.incident.entity.User;
import com.attt.incident.repository.RefreshTokenRepository;
import com.attt.incident.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * One-shot admin recovery tool.
 *
 * <p>Activates <strong>only</strong> when the Spring profile
 * {@code admin-recovery} is active. The application exits immediately after
 * this runner completes, so it cannot remain running as an HTTP server.
 *
 * <h3>Usage</h3>
 * <pre>
 * RECOVERY_ADMIN_NEW_PASSWORD="ChangeMe@$(date +%s)!" \
 *   java -Dspring.profiles.active=admin-recovery \
 *        -jar incident-management.jar
 * </pre>
 *
 * <h3>Security contract</h3>
 * <ul>
 *   <li>New password is read from the environment variable
 *       {@code RECOVERY_ADMIN_NEW_PASSWORD} — never from a command-line
 *       argument, JVM property, or application.properties to avoid leaking
 *       it in process lists or config files.</li>
 *   <li>Password must satisfy the policy: ≥ 12 chars, at least one upper,
 *       one lower, one digit, one special character.</li>
 *   <li>New password must not match the seed hash
 *       ({@code Admin@123}) or be a common weak password.</li>
 *   <li>After a successful reset: account is re-enabled, {@code token_version}
 *       is incremented (all previously issued JWTs become invalid),
 *       {@code password_changed_at} is updated, and all refresh tokens for
 *       the admin account are deleted.</li>
 *   <li>No HTTP endpoint is involved.</li>
 * </ul>
 */
@Slf4j
@Component
@Profile("admin-recovery")
@RequiredArgsConstructor
public class AdminRecoveryRunner implements CommandLineRunner {

    // BCrypt hash of "Admin@123" (the V2 seed password).
    // We compare the new password against the hash to prevent reuse.
    private static final String SEED_ADMIN_HASH =
            "$2b$10$TQ1B5UvVETjjqKOsPtAqG.tGCIQEJKAtL0HVTIcSEqp8ZBhjpF3ca";

    // Common weak passwords that must be rejected even if they pass the regex.
    private static final java.util.Set<String> WEAK_PASSWORDS = java.util.Set.of(
            "Admin@123", "Password@1", "P@ssword1", "P@ssw0rd",
            "Welcome@1", "Changeme@1", "Admin@1234"
    );

    private static final String ENV_VAR = "RECOVERY_ADMIN_NEW_PASSWORD";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.warn("=== ADMIN RECOVERY MODE ACTIVE — application will exit after this runner ===");

        // ── 1. Read new password from environment ────────────────────
        String newPassword = System.getenv(ENV_VAR);
        if (newPassword == null || newPassword.isBlank()) {
            log.error("RECOVERY FAILED: environment variable {} is not set or blank.", ENV_VAR);
            log.error("Set the variable and retry. Example:");
            log.error("  export {}=\"YourNewPassword@$(date +%s)!\"", ENV_VAR);
            System.exit(2);
            return;
        }

        // ── 2. Validate password policy ──────────────────────────────
        String policyError = validatePasswordPolicy(newPassword);
        if (policyError != null) {
            log.error("RECOVERY FAILED: new password does not meet policy: {}", policyError);
            System.exit(3);
            return;
        }

        // ── 3. Reject the original seed password ─────────────────────
        if (passwordEncoder.matches(newPassword, SEED_ADMIN_HASH)) {
            log.error("RECOVERY FAILED: new password must not be the original seed password (Admin@123).");
            System.exit(4);
            return;
        }

        // ── 4. Reject commonly weak passwords ────────────────────────
        if (WEAK_PASSWORDS.contains(newPassword)) {
            log.error("RECOVERY FAILED: new password is on the common weak-password list.");
            System.exit(5);
            return;
        }

        // ── 5. Load admin user ───────────────────────────────────────
        User admin = userRepository.findByUsername("admin").orElse(null);
        if (admin == null) {
            log.error("RECOVERY FAILED: user 'admin' not found in database.");
            System.exit(6);
            return;
        }

        // ── 6. Verify admin has ADMIN role ───────────────────────────
        boolean hasAdminRole = admin.getRoles().stream()
                .anyMatch(r -> r.getName() == RoleName.ADMIN);
        if (!hasAdminRole) {
            log.error("RECOVERY FAILED: user 'admin' does not have the ADMIN role. Manual intervention required.");
            System.exit(7);
            return;
        }

        // ── 7. Apply recovery ────────────────────────────────────────
        String newHash = passwordEncoder.encode(newPassword);
        admin.setPassword(newHash);
        admin.setEnabled(true);
        admin.setFailedLoginAttempts(0);
        admin.setAccountLockedUntil(null);
        admin.setTokenVersion(admin.getTokenVersion() + 1);  // invalidate old JWTs
        admin.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(admin);

        // ── 8. Revoke all refresh tokens for admin ───────────────────
        int deleted = refreshTokenRepository.deleteByUser(admin);
        log.info("RECOVERY: {} refresh token(s) revoked for admin.", deleted);

        log.warn("=== ADMIN RECOVERY SUCCESSFUL ===");
        log.warn("  • Account enabled: true");
        log.warn("  • Password updated and hashed (BCrypt)");
        log.warn("  • token_version incremented — all existing JWTs for admin are now INVALID");
        log.warn("  • All refresh tokens for admin revoked");
        log.warn("  • password_changed_at updated to {}", admin.getPasswordChangedAt());
        log.warn("  • IMPORTANT: clear the {} environment variable immediately!", ENV_VAR);
        log.warn("  • IMPORTANT: log in with the new password and change it again via the UI.");
        log.warn("=================================================================");

        // Force application exit so this JAR cannot be reused as an HTTP server.
        System.exit(0);
    }

    // ── Password policy validation ────────────────────────────────────

    /**
     * Returns an error message if the password violates policy, or {@code null}
     * if the password is acceptable.
     *
     * <p>Policy: minimum 12 characters, at least one uppercase letter, one
     * lowercase letter, one digit, and one special character.
     */
    static String validatePasswordPolicy(String password) {
        if (password == null || password.length() < 12) {
            return "Password must be at least 12 characters long.";
        }
        if (!password.chars().anyMatch(Character::isUpperCase)) {
            return "Password must contain at least one uppercase letter.";
        }
        if (!password.chars().anyMatch(Character::isLowerCase)) {
            return "Password must contain at least one lowercase letter.";
        }
        if (!password.chars().anyMatch(Character::isDigit)) {
            return "Password must contain at least one digit.";
        }
        if (!password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
            return "Password must contain at least one special character.";
        }
        return null; // passes all checks
    }
}
