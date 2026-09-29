package com.attt.incident.repository;

import com.attt.incident.entity.SlaAlertHistory;
import com.attt.incident.entity.SlaAlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SlaAlertHistoryRepository extends JpaRepository<SlaAlertHistory, Long> {
    boolean existsByIncidentIdAndAlertTypeAndRecipientKey(
            Long incidentId, SlaAlertType alertType, String recipientKey);

    @Query("SELECT COUNT(h) > 0 FROM SlaAlertHistory h "
            + "WHERE h.incident.id = :incidentId AND h.alertType = :alertType "
            + "AND h.recipientKey LIKE 'LEGACY_SCOPE:%'")
    boolean existsLegacyAggregate(
            @org.springframework.data.repository.query.Param("incidentId") Long incidentId,
            @org.springframework.data.repository.query.Param("alertType") SlaAlertType alertType);

    @Query(value = "SELECT pg_try_advisory_xact_lock(7242026)", nativeQuery = true)
    boolean acquireSchedulerLock();
}
