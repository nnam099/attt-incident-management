package com.attt.incident.repository;

import com.attt.incident.entity.SecurityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {
}
