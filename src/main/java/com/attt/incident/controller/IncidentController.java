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
}
