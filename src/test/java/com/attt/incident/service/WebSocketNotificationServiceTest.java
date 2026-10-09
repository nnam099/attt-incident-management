package com.attt.incident.service;

import com.attt.incident.dto.IncidentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketNotificationServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketNotificationService service;

    @Test
    void updateInvalidatesStaffAndOnlyTheRelatedUsersWithoutIncidentDetails() {
        IncidentResponse incident = IncidentResponse.builder()
                .incidentCode("INC-2026-000001")
                .reportedByUsername("reporter")
                .assignedToUsername("analyst")
                .build();
        Map<String, String> signal = Map.of("type", "INCIDENT_UPDATED");

        service.notifyIncidentUpdate(incident);

        verify(messagingTemplate).convertAndSend("/topic/incidents", signal);
        verify(messagingTemplate).convertAndSendToUser("reporter", "/queue/incidents", signal);
        verify(messagingTemplate).convertAndSendToUser("analyst", "/queue/incidents", signal);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void reassignmentInvalidatesPreviousAssigneeOnlyWhenItChanged() {
        service.notifyPreviousAssignee("old-analyst", "new-analyst");
        service.notifyPreviousAssignee("new-analyst", "new-analyst");
        service.notifyPreviousAssignee(null, "new-analyst");

        verify(messagingTemplate).convertAndSendToUser("old-analyst", "/queue/incidents",
                Map.of("type", "INCIDENT_UPDATED"));
        verifyNoMoreInteractions(messagingTemplate);
    }
}
