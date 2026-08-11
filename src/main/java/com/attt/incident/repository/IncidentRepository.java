package com.attt.incident.repository;

import com.attt.incident.entity.Incident;
import com.attt.incident.entity.IncidentSeverity;
import com.attt.incident.entity.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);

    Page<Incident> findBySeverity(IncidentSeverity severity, Pageable pageable);

    Page<Incident> findByAssignedToId(Long userId, Pageable pageable);

    Page<Incident> findByReportedById(Long userId, Pageable pageable);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.status <> 'CLOSED'")
    long countOpenIncidents();

    @Query("SELECT i.severity, COUNT(i) FROM Incident i WHERE i.status <> 'CLOSED' GROUP BY i.severity")
    java.util.List<Object[]> countOpenBySeverity();

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.incidentCode LIKE CONCAT(:prefix, '%')")
    long countByCodePrefix(@Param("prefix") String prefix);

    @Query("SELECT i.status, COUNT(i) FROM Incident i GROUP BY i.status")
    java.util.List<Object[]> countByStatus();

    @Query("SELECT i.category.name, COUNT(i) FROM Incident i GROUP BY i.category.name")
    java.util.List<Object[]> countByCategory();

    @Query("SELECT i FROM Incident i WHERE i.status = 'CLOSED' AND i.closedAt IS NOT NULL")
    java.util.List<Incident> findClosedIncidents();

    @Query("SELECT i FROM Incident i WHERE i.createdAt >= :startDate AND i.createdAt <= :endDate")
    java.util.List<Incident> findIncidentsByTimeFrame(@Param("startDate") java.time.LocalDateTime startDate, @Param("endDate") java.time.LocalDateTime endDate);

    @Query("SELECT i FROM Incident i WHERE i.status <> 'CLOSED' AND i.slaWarningSent = false AND i.slaDueAt <= :thresholdTime")
    java.util.List<Incident> findIncidentsApproachingSla(@Param("thresholdTime") java.time.LocalDateTime thresholdTime);
}
