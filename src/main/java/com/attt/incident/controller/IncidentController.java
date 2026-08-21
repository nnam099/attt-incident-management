package com.attt.incident.controller;

import com.attt.incident.dto.AssignRequest;
import com.attt.incident.dto.IncidentCreateRequest;
import com.attt.incident.dto.IncidentResponse;
import com.attt.incident.dto.StatusUpdateRequest;
import com.attt.incident.service.IncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;

    @PostMapping
    public ResponseEntity<IncidentResponse> createIncident(
            @Valid @RequestBody IncidentCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.createIncident(request, authentication));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentResponse> getIncident(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.getIncident(id, authentication));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<IncidentResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.changeStatus(id, request, authentication));
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<IncidentResponse> assignIncident(
            @PathVariable Long id,
            @Valid @RequestBody AssignRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.assignIncident(id, request, authentication));
    }
    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<IncidentResponse>> getIncidents(
            @RequestParam(required = false) com.attt.incident.entity.IncidentStatus status,
            @RequestParam(required = false) com.attt.incident.entity.IncidentSeverity severity,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean overdue,
            org.springframework.data.domain.Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.getIncidents(status, severity, assigneeId, keyword, overdue, pageable, authentication));
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<java.util.List<com.attt.incident.dto.LogResponse>> getIncidentLogs(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.getIncidentLogs(id, authentication));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IncidentResponse> updateIncident(
            @PathVariable Long id,
            @Valid @RequestBody com.attt.incident.dto.IncidentUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.updateIncident(id, request, authentication));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<com.attt.incident.dto.LogResponse> addComment(
            @PathVariable Long id,
            @Valid @RequestBody com.attt.incident.dto.CommentRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.addComment(id, request, authentication));
    }
    @PostMapping("/{id}/iocs")
    public ResponseEntity<com.attt.incident.dto.IoCResponse> addIoC(
            @PathVariable Long id,
            @Valid @RequestBody com.attt.incident.dto.IoCRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.addIoC(id, request, authentication));
    }

    @DeleteMapping("/{id}/iocs/{iocId}")
    public ResponseEntity<Void> deleteIoC(
            @PathVariable Long id,
            @PathVariable Long iocId,
            Authentication authentication) {
        incidentService.deleteIoC(id, iocId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/tasks")
    public ResponseEntity<com.attt.incident.dto.TaskResponse> addTask(
            @PathVariable Long id,
            @Valid @RequestBody com.attt.incident.dto.TaskRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.addTask(id, request, authentication));
    }

    @PatchMapping("/{id}/tasks/{taskId}/toggle")
    public ResponseEntity<com.attt.incident.dto.TaskResponse> toggleTask(
            @PathVariable Long id,
            @PathVariable Long taskId,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.toggleTask(id, taskId, authentication));
    }
}
