package com.attt.incident.dto;

import com.attt.incident.entity.IncidentSeverity;
import com.attt.incident.entity.IncidentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import com.attt.incident.entity.ResolutionType;

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
    private LocalDateTime ackDueAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolveDueAt;
    private ResolutionType resolutionType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<IoCResponse> iocs;
    private List<TaskResponse> tasks;
}
