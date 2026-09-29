package com.attt.incident.job;

import com.attt.incident.entity.*;
import com.attt.incident.repository.IncidentRepository;
import com.attt.incident.repository.SlaAlertHistoryRepository;
import com.attt.incident.repository.UserRepository;
import com.attt.incident.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlaMonitorJobTest {
    @Mock IncidentRepository incidentRepository;
    @Mock SlaAlertHistoryRepository alertHistoryRepository;
    @Mock UserRepository userRepository;
    @Mock EmailService emailService;
    @InjectMocks SlaMonitorJob job;

    @Test
    void successfulDeliveryIsRecordedPerRecipient() {
        User analyst = User.builder().id(2L).username("analyst").email("Analyst@Example.com").build();
        Incident incident = Incident.builder().id(4L).incidentCode("INC-2026-000004")
                .title("Phishing").assignedTo(analyst).ackDueAt(LocalDateTime.now().plusMinutes(30)).build();
        when(alertHistoryRepository.acquireSchedulerLock()).thenReturn(true);
        when(incidentRepository.findAckApproachingSla(any(), any())).thenReturn(List.of(incident));
        when(incidentRepository.findAckBreachedSla(any())).thenReturn(List.of());
        when(incidentRepository.findResolveApproachingSla(any(), any())).thenReturn(List.of());
        when(incidentRepository.findResolveBreachedSla(any())).thenReturn(List.of());
        when(userRepository.findByRoleName(RoleName.HELPDESK)).thenReturn(List.of());
        when(emailService.sendEmail(eq("Analyst@Example.com"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));

        job.scanAndAlertSla();

        ArgumentCaptor<SlaAlertHistory> captor = ArgumentCaptor.forClass(SlaAlertHistory.class);
        verify(alertHistoryRepository).save(captor.capture());
        assertEquals("analyst@example.com", captor.getValue().getRecipientKey());
        assertEquals(SlaAlertType.ACK_WARNING, captor.getValue().getAlertType());
    }
}
