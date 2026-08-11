package com.attt.incident.repository;

import com.attt.incident.entity.IncidentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentLogRepository extends JpaRepository<IncidentLog, Long> {
    List<IncidentLog> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
    List<IncidentLog> findTopByIncidentIdOrderByCreatedAtDesc(Long incidentId);
}
