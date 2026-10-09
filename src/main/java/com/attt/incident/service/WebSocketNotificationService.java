package com.attt.incident.service;

import com.attt.incident.dto.IncidentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /** Send only invalidation signals; incident details are fetched through the authorized API. */
    public void notifyIncidentUpdate(IncidentResponse incidentResponse) {
        log.info("Pushing real-time update: Sự cố {}", incidentResponse.getIncidentCode());
        var signal = java.util.Map.of("type", "INCIDENT_UPDATED");
        messagingTemplate.convertAndSend("/topic/incidents", signal);
        java.util.Set<String> recipients = new java.util.HashSet<>();
        if (incidentResponse.getReportedByUsername() != null) {
            recipients.add(incidentResponse.getReportedByUsername());
        }
        if (incidentResponse.getAssignedToUsername() != null) {
            recipients.add(incidentResponse.getAssignedToUsername());
        }
        recipients.forEach(username -> messagingTemplate.convertAndSendToUser(
                username, "/queue/incidents", signal));
    }

    /** A previous assignee must also remove a reassigned incident from their queue. */
    public void notifyPreviousAssignee(String username, String currentAssignee) {
        if (username != null && !username.equals(currentAssignee)) {
            messagingTemplate.convertAndSendToUser(username, "/queue/incidents",
                    java.util.Map.of("type", "INCIDENT_UPDATED"));
        }
    }

    /**
     * Gửi thông báo phân công cá nhân cho một user cụ thể
     */
    public void notifyUserAssignment(String username, IncidentResponse incidentResponse) {
        log.info("Pushing real-time notification cho user {}: Sự cố {}", username, incidentResponse.getIncidentCode());
        String message = "Bạn được phân công xử lý sự cố "
                + incidentResponse.getIncidentCode() + ": " + incidentResponse.getTitle();
        messagingTemplate.convertAndSendToUser(username, "/queue/notifications", message);
    }
}
