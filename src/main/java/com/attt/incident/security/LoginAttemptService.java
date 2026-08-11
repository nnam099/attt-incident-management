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

    // Bộ nhớ tạm giới hạn IP cục bộ (đơn giản hóa)
    private final ConcurrentHashMap<String, Integer> ipAttemptsCache = new ConcurrentHashMap<>();

    @Transactional
    public void loginSucceeded(String username, String ipAddress) {
        ipAttemptsCache.remove(ipAddress);
        
        Optional<User> userOpt = userRepository.findByUsername(username);
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
        int attempts = ipAttemptsCache.getOrDefault(ipAddress, 0);
        attempts++;
        ipAttemptsCache.put(ipAddress, attempts);

        Optional<User> userOpt = userRepository.findByUsername(username);
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
        if (attempts >= 10) {
            logAudit(username, ipAddress, "IP_BLOCKED_WARNING", "IP này đã đăng nhập sai " + attempts + " lần liên tiếp.");
        }
    }

    public boolean isAccountLocked(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
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

    private void logAudit(String username, String ip, String action, String details) {
        SecurityAuditLog auditLog = SecurityAuditLog.builder()
                .username(username)
                .ipAddress(ip)
                .action(action)
                .details(details)
                .build();
        auditLogRepository.save(auditLog);
    }
}
