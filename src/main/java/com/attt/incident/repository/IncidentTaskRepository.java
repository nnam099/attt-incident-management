package com.attt.incident.repository;

import com.attt.incident.entity.IncidentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentTaskRepository extends JpaRepository<IncidentTask, Long> {
}
