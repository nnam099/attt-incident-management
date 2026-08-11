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

    /**
     * Gửi cập nhật sự cố ra toàn hệ thống (Dashboard lắng nghe)
     */
    public void notifyIncidentUpdate(IncidentResponse incidentResponse) {
        log.info("Pushing real-time update cho Dashboard: Sự cố {}", incidentResponse.getIncidentCode());
        messagingTemplate.convertAndSend("/topic/incidents", incidentResponse);
    }

    /**
     * Gửi thông báo phân công cá nhân cho một user cụ thể
     */
    public void notifyUserAssignment(String username, IncidentResponse incidentResponse) {
        log.info("Pushing real-time notification cho user {}: Sự cố {}", username, incidentResponse.getIncidentCode());
        // Frontend sẽ subscribe vào /user/{username}/queue/notifications
        String message = String.format("Bạn vừa được phân công xử lý sự cố: %s - %s", 
                incidentResponse.getIncidentCode(), incidentResponse.getTitle());
        messagingTemplate.convertAndSendToUser(username, "/queue/notifications", message);
    }
}
