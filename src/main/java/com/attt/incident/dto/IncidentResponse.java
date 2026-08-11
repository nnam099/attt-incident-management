package com.attt.incident.dto;

import com.attt.incident.entity.IncidentSeverity;
import com.attt.incident.entity.IncidentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class IncidentResponse {
    private Long id;
    private String incidentCode;
    private String title;
    private String description; // sẽ được ẩn/che theo quyền ở tầng service
    private String affectedSystem;
    private String categoryName;
    private IncidentSeverity severity;
    private IncidentStatus status;
    private String reportedByUsername;
    private String assignedToUsername;
    private LocalDateTime detectedAt;
    private LocalDateTime slaDueAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
