package com.attt.incident.repository;

import com.attt.incident.entity.IncidentCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentCategoryRepository extends JpaRepository<IncidentCategory, Long> {
}
