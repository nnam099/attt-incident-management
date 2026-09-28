package com.attt.incident.job;

import com.attt.incident.entity.Incident;
import com.attt.incident.entity.RoleName;
import com.attt.incident.entity.SlaAlertHistory;
import com.attt.incident.entity.SlaAlertType;
import com.attt.incident.entity.User;
import com.attt.incident.repository.IncidentRepository;
import com.attt.incident.repository.SlaAlertHistoryRepository;
import com.attt.incident.repository.UserRepository;
import com.attt.incident.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlaMonitorJob {

    private final IncidentRepository incidentRepository;
    private final SlaAlertHistoryRepository alertHistoryRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    // Quét mỗi 15 phút (900000 ms)
    @Scheduled(fixedRate = 900000)
    @Transactional
    public void scanAndAlertSla() {
        if (!alertHistoryRepository.acquireSchedulerLock()) {
            log.debug("Bỏ qua lượt quét SLA vì replica khác đang thực thi");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusHours(2);
        alert(incidentRepository.findAckApproachingSla(now, threshold), SlaAlertType.ACK_WARNING, false);
        alert(incidentRepository.findAckBreachedSla(now), SlaAlertType.ACK_BREACHED, true);
        alert(incidentRepository.findResolveApproachingSla(now, threshold), SlaAlertType.RESOLVE_WARNING, false);
        alert(incidentRepository.findResolveBreachedSla(now), SlaAlertType.RESOLVE_BREACHED, true);
    }

    private void alert(List<Incident> incidents, SlaAlertType type, boolean escalate) {
        for (Incident incident : incidents) {
            if (alertHistoryRepository.existsByIncidentIdAndAlertType(incident.getId(), type)) continue;
            LocalDateTime dueAt = type.name().startsWith("ACK_") ? incident.getAckDueAt() : incident.getResolveDueAt();
            String subject = "[SLA " + (escalate ? "QUÁ HẠN" : "SẮP HẾT HẠN") + "] " + incident.getIncidentCode();
            String body = "Sự cố " + incident.getIncidentCode() + " (" + incident.getTitle() + ") "
                    + (escalate ? "đã quá hạn" : "sắp đến hạn") + " vào " + dueAt + ".";
            java.util.List<java.util.concurrent.CompletableFuture<Boolean>> deliveries = new java.util.ArrayList<>();
            if (incident.getAssignedTo() != null) queueDelivery(deliveries, incident.getAssignedTo().getEmail(), subject, body);
            if (type.name().startsWith("ACK_")) userRepository.findByRoleName(RoleName.HELPDESK)
                    .forEach(u -> queueDelivery(deliveries, u.getEmail(), subject, body));
            if (escalate) userRepository.findByRoleName(RoleName.MANAGER)
                    .forEach(u -> queueDelivery(deliveries, u.getEmail(), subject, body));

            boolean delivered = !deliveries.isEmpty();
            for (java.util.concurrent.CompletableFuture<Boolean> delivery : deliveries) {
                try {
                    delivered &= Boolean.TRUE.equals(delivery.join());
                } catch (java.util.concurrent.CompletionException ex) {
                    delivered = false;
                    log.warn("Tác vụ gửi SLA alert thất bại", ex);
                }
            }
            if (delivered) {
                alertHistoryRepository.save(SlaAlertHistory.builder().incident(incident).alertType(type)
                        .recipientScope(escalate ? "ESCALATED" : "OPERATIONAL").build());
            } else {
                log.warn("Chưa gửi được SLA alert {} cho {}; sẽ thử lại ở lượt sau", type, incident.getIncidentCode());
            }
        }
    }

    private void queueDelivery(java.util.List<java.util.concurrent.CompletableFuture<Boolean>> deliveries,
                               String recipient, String subject, String body) {
        try {
            deliveries.add(emailService.sendEmail(recipient, subject, body));
        } catch (RuntimeException ex) {
            log.warn("Không thể xếp hàng SLA alert cho {}", recipient, ex);
            deliveries.add(java.util.concurrent.CompletableFuture.completedFuture(false));
        }
    }
}
