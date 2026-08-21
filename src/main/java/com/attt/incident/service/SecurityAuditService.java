package com.attt.incident.service;

import com.attt.incident.entity.SecurityAuditLog;
import com.attt.incident.repository.SecurityAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SecurityAuditService {
    private final SecurityAuditLogRepository repository;

    @Transactional
    public void log(String action, String details) {
        String username = SecurityContextHolder.getContext().getAuthentication() == null
                ? "SYSTEM" : SecurityContextHolder.getContext().getAuthentication().getName();
        repository.save(SecurityAuditLog.builder().username(username).action(action).details(details).build());
    }
}
