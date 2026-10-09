package com.attt.incident.repository;

import com.attt.incident.entity.Incident;
import com.attt.incident.entity.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Incident i WHERE i.id = :id")
    java.util.Optional<Incident> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "SELECT nextval('incident_code_seq')", nativeQuery = true)
    long nextIncidentCodeSequence();

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.status <> 'CLOSED'")
    long countOpenIncidents();

    @Query("SELECT i.severity, COUNT(i) FROM Incident i WHERE i.status <> 'CLOSED' GROUP BY i.severity")
    java.util.List<Object[]> countOpenBySeverity();

    @Query("SELECT i.status, COUNT(i) FROM Incident i GROUP BY i.status")
    java.util.List<Object[]> countByStatus();

    @Query("SELECT i.category.name, COUNT(i) FROM Incident i GROUP BY i.category.name")
    java.util.List<Object[]> countByCategory();

    @Query("SELECT i FROM Incident i WHERE i.status = 'CLOSED' AND i.closedAt IS NOT NULL")
    java.util.List<Incident> findClosedIncidents();

    @Query("SELECT i FROM Incident i WHERE i.createdAt >= :startDate AND i.createdAt <= :endDate")
    java.util.List<Incident> findIncidentsByTimeFrame(@Param("startDate") java.time.LocalDateTime startDate, @Param("endDate") java.time.LocalDateTime endDate);

    @Query("SELECT i FROM Incident i WHERE i.status = 'NEW' AND i.acknowledgedAt IS NULL AND i.ackDueAt > :now AND i.ackDueAt <= :threshold")
    java.util.List<Incident> findAckApproachingSla(@Param("now") java.time.LocalDateTime now, @Param("threshold") java.time.LocalDateTime threshold);

    @Query("SELECT i FROM Incident i WHERE i.status = 'NEW' AND i.acknowledgedAt IS NULL AND i.ackDueAt <= :now")
    java.util.List<Incident> findAckBreachedSla(@Param("now") java.time.LocalDateTime now);

    @Query("SELECT i FROM Incident i WHERE i.status NOT IN ('RESOLVED', 'CLOSED') AND i.resolveDueAt > :now AND i.resolveDueAt <= :threshold")
    java.util.List<Incident> findResolveApproachingSla(@Param("now") java.time.LocalDateTime now, @Param("threshold") java.time.LocalDateTime threshold);

    @Query("SELECT i FROM Incident i WHERE i.status NOT IN ('RESOLVED', 'CLOSED') AND i.resolveDueAt <= :now")
    java.util.List<Incident> findResolveBreachedSla(@Param("now") java.time.LocalDateTime now);
}
