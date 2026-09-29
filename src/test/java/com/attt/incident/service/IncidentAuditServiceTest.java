package com.attt.incident.service;

import com.attt.incident.entity.Incident;
import com.attt.incident.entity.IncidentLog;
import com.attt.incident.entity.User;
import com.attt.incident.repository.IncidentLogRepository;
import com.attt.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentAuditServiceTest {
    @Mock IncidentLogRepository logRepository;
    @Mock IncidentRepository incidentRepository;
    @InjectMocks IncidentAuditService auditService;

    @Test
    void appendLocksIncidentAndProducesVerifiableChain() {
        Incident incident = Incident.builder().id(12L).build();
        User actor = User.builder().id(3L).username("analyst").build();
        when(incidentRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(incident));
        when(logRepository.findFirstByIncidentIdOrderByIdDesc(12L)).thenReturn(Optional.empty());
        when(logRepository.save(any(IncidentLog.class))).thenAnswer(invocation -> {
            IncidentLog log = invocation.getArgument(0);
            log.setId(1L);
            return log;
        });

        IncidentLog saved = auditService.append(incident, actor, "COMMENT", null, null, "evidence");
        when(logRepository.findByIncidentIdOrderByIdAsc(12L)).thenReturn(List.of(saved));

        var result = auditService.verify(12L);

        assertTrue(result.valid());
        assertEquals(1, result.checkedRecords());
        verify(incidentRepository).findByIdForUpdate(12L);
    }

    @Test
    void verifyDetectsModifiedAuditRecord() {
        IncidentLog log = IncidentLog.builder().id(9L).incident(Incident.builder().id(12L).build())
                .performedBy(User.builder().id(3L).build()).actionType("COMMENT")
                .note("modified").recordHash("invalid").createdAt(java.time.LocalDateTime.now()).build();
        when(logRepository.findByIncidentIdOrderByIdAsc(12L)).thenReturn(List.of(log));

        var result = auditService.verify(12L);

        assertFalse(result.valid());
        assertEquals(9L, result.firstInvalidLogId());
    }
}
