package com.attt.incident.repository;

import com.attt.incident.entity.SlaAlertHistory;
import com.attt.incident.entity.SlaAlertType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaAlertHistoryRepository extends JpaRepository<SlaAlertHistory, Long> {
    boolean existsByIncidentIdAndAlertType(Long incidentId, SlaAlertType alertType);
}
