package com.attt.incident.repository;

import com.attt.incident.entity.IncidentAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentAttachmentRepository extends JpaRepository<IncidentAttachment, Long> {
    List<IncidentAttachment> findByIncidentId(Long incidentId);
}
