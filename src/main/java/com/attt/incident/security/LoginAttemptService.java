package com.attt.incident.security;

import com.attt.incident.entity.SecurityAuditLog;
import com.attt.incident.entity.User;
import com.attt.incident.repository.SecurityAuditLogRepository;
import com.attt.incident.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final SecurityAuditLogRepository auditLogRepository;

    public static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_TIME_DURATION = 15; // Phút
    private static final int IP_MAX_ATTEMPTS = 10;
    private static final int MAX_TRACKED_IPS = 10_000;

    // Bộ nhớ tạm giới hạn IP cục bộ (đơn giản hóa)
    private final ConcurrentHashMap<String, IpAttempt> ipAttemptsCache = new ConcurrentHashMap<>();

    @Transactional
    public void loginSucceeded(String username, String ipAddress) {
        ipAttemptsCache.remove(ipAddress);
        
        Optional<User> userOpt = userRepository.findByUsernameForUpdate(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getFailedLoginAttempts() > 0) {
                user.setFailedLoginAttempts(0);
                user.setAccountLockedUntil(null);
                userRepository.save(user);
            }
        }
    }

    @Transactional
    public void loginFailed(String username, String ipAddress) {
        // Tăng đếm IP
        LocalDateTime now = LocalDateTime.now();
        cleanupIpCache(now);
        IpAttempt ipAttempt = null;
        if (ipAttemptsCache.containsKey(ipAddress) || ipAttemptsCache.size() < MAX_TRACKED_IPS) {
            ipAttempt = ipAttemptsCache.compute(ipAddress, (ignored, previous) -> {
                int count = previous == null ? 1 : previous.count() + 1;
                LocalDateTime blockedUntil = count >= IP_MAX_ATTEMPTS
                        ? now.plusMinutes(LOCK_TIME_DURATION) : null;
                return new IpAttempt(count, blockedUntil, now);
            });
        }

        Optional<User> userOpt = userRepository.findByUsernameForUpdate(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            // Nếu tài khoản đang mở khóa, tăng số lần thử
            if (user.getAccountLockedUntil() == null || user.getAccountLockedUntil().isBefore(LocalDateTime.now())) {
                user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

                if (user.getFailedLoginAttempts() >= MAX_ATTEMPTS) {
                    user.setAccountLockedUntil(LocalDateTime.now().plusMinutes(LOCK_TIME_DURATION));
                    logAudit(username, ipAddress, "ACCOUNT_LOCKED", "Tài khoản bị khóa " + LOCK_TIME_DURATION + " phút do đăng nhập sai quá " + MAX_ATTEMPTS + " lần.");
                } else {
                    logAudit(username, ipAddress, "LOGIN_FAILED", "Sai mật khẩu lần " + user.getFailedLoginAttempts());
                }
                userRepository.save(user);
            }
        } else {
            logAudit(username, ipAddress, "LOGIN_FAILED", "Username không tồn tại.");
        }

        // Nếu 1 IP sai quá 10 lần liên tục (kể cả khác username), có thể chặn IP (Mở rộng)
        if (ipAttempt != null && ipAttempt.blockedUntil() != null) {
            logAudit(username, ipAddress, "IP_BLOCKED", "IP bị khóa tạm thời sau " + ipAttempt.count() + " lần đăng nhập sai.");
        }
    }

    @Transactional
    public boolean isAccountLocked(String username) {
        Optional<User> userOpt = userRepository.findByUsernameForUpdate(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getAccountLockedUntil() != null) {
                if (user.getAccountLockedUntil().isAfter(LocalDateTime.now())) {
                    return true;
                } else {
                    // Đã hết thời gian khóa, reset lại
                    user.setAccountLockedUntil(null);
                    user.setFailedLoginAttempts(0);
                    userRepository.save(user);
                    return false;
                }
            }
        }
        return false;
    }

    public boolean isIpBlocked(String ipAddress) {
        IpAttempt attempt = ipAttemptsCache.get(ipAddress);
        if (attempt == null || attempt.blockedUntil() == null) return false;
        if (attempt.blockedUntil().isAfter(LocalDateTime.now())) return true;
        ipAttemptsCache.remove(ipAddress, attempt);
        return false;
    }

    private void cleanupIpCache(LocalDateTime now) {
        if (ipAttemptsCache.size() < MAX_TRACKED_IPS) return;
        ipAttemptsCache.entrySet().removeIf(entry ->
                entry.getValue().lastAttempt().isBefore(now.minusMinutes(LOCK_TIME_DURATION)));
    }

    private void logAudit(String username, String ip, String action, String details) {
        SecurityAuditLog auditLog = SecurityAuditLog.builder()
                .username(username)
                .ipAddress(ip)
                .action(action)
                .details(details)
                .build();
        auditLogRepository.save(auditLog);
    }

    private record IpAttempt(int count, LocalDateTime blockedUntil, LocalDateTime lastAttempt) {}
}
