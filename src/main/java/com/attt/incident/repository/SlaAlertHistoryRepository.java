package com.attt.incident.repository;

import com.attt.incident.entity.SlaAlertHistory;
import com.attt.incident.entity.SlaAlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SlaAlertHistoryRepository extends JpaRepository<SlaAlertHistory, Long> {
    boolean existsByIncidentIdAndAlertType(Long incidentId, SlaAlertType alertType);

    @Query(value = "SELECT pg_try_advisory_xact_lock(7242026)", nativeQuery = true)
    boolean acquireSchedulerLock();
}
