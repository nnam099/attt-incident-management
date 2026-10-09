package com.attt.incident.service;

import com.attt.incident.entity.Incident;
import com.attt.incident.entity.IncidentSeverity;
import com.attt.incident.entity.IncidentStatus;
import com.attt.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    @Test
    void emptyDashboardDoesNotClaimPerfectSlaCompliance() {
        IncidentRepository repository = emptyRepository();

        var dashboard = new ReportService(repository).getDashboardStats("month");

        assertThat(dashboard.getSlaComplianceRate()).isZero();
        assertThat(dashboard.getTotalOpenIncidents()).isZero();
    }

    @Test
    void averageResolutionTimeRetainsSubHourPrecision() {
        IncidentRepository repository = emptyRepository();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 1, 8, 0);
        Incident closed = Incident.builder()
                .severity(IncidentSeverity.HIGH)
                .status(IncidentStatus.CLOSED)
                .createdAt(createdAt)
                .resolvedAt(createdAt.plusMinutes(90))
                .build();
        when(repository.findIncidentsByTimeFrame(any(), any())).thenReturn(List.of(closed));

        var dashboard = new ReportService(repository).getDashboardStats("week");

        assertThat(dashboard.getAverageResolutionTimeHoursBySeverity().get("HIGH"))
                .isEqualTo(1.5);
    }

    @Test
    void dashboardSummaryUsesOnlySelectedTimeFrame() {
        IncidentRepository repository = emptyRepository();
        Incident open = Incident.builder()
                .severity(IncidentSeverity.CRITICAL)
                .status(IncidentStatus.REOPENED)
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();
        when(repository.findIncidentsByTimeFrame(any(), any())).thenReturn(List.of(open));

        var dashboard = new ReportService(repository).getDashboardStats("week");

        assertThat(dashboard.getTotalOpenIncidents()).isEqualTo(1);
        assertThat(dashboard.getIncidentsByStatus()).containsEntry("REOPENED", 1L);
        assertThat(dashboard.getIncidentsBySeverity()).containsEntry("CRITICAL", 1L);
    }

    private IncidentRepository emptyRepository() {
        IncidentRepository repository = mock(IncidentRepository.class);
        when(repository.countOpenBySeverity()).thenReturn(List.of());
        when(repository.countByStatus()).thenReturn(List.of());
        when(repository.countByCategory()).thenReturn(List.of());
        when(repository.findClosedIncidents()).thenReturn(List.of());
        when(repository.findIncidentsByTimeFrame(any(), any())).thenReturn(List.of());
        when(repository.findAll()).thenReturn(List.of());
        return repository;
    }
}
