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
            if (alertHistoryRepository.existsLegacyAggregate(incident.getId(), type)) continue;
            LocalDateTime dueAt = type.name().startsWith("ACK_") ? incident.getAckDueAt() : incident.getResolveDueAt();
            String subject = "[SLA " + (escalate ? "QUÁ HẠN" : "SẮP HẾT HẠN") + "] " + incident.getIncidentCode();
            String body = "Sự cố " + incident.getIncidentCode() + " (" + incident.getTitle() + ") "
                    + (escalate ? "đã quá hạn" : "sắp đến hạn") + " vào " + dueAt + ".";
            java.util.LinkedHashMap<String, Recipient> recipients = new java.util.LinkedHashMap<>();
            if (incident.getAssignedTo() != null) addRecipient(recipients, incident.getAssignedTo(), "OPERATIONAL");
            if (type.name().startsWith("ACK_")) userRepository.findByRoleName(RoleName.HELPDESK)
                    .forEach(user -> addRecipient(recipients, user, "OPERATIONAL"));
            if (escalate) userRepository.findByRoleName(RoleName.MANAGER)
                    .forEach(user -> addRecipient(recipients, user, "ESCALATED"));

            recipients.values().forEach(recipient -> deliverOnce(
                    incident, type, recipient, subject, body));
        }
    }

    private void addRecipient(java.util.Map<String, Recipient> recipients, User user, String scope) {
        if (user.getEmail() == null || user.getEmail().isBlank()) return;
        String key = user.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        recipients.putIfAbsent(key, new Recipient(key, user.getEmail().trim(), scope));
    }

    private void deliverOnce(Incident incident, SlaAlertType type, Recipient recipient,
                             String subject, String body) {
        if (alertHistoryRepository.existsByIncidentIdAndAlertTypeAndRecipientKey(
                incident.getId(), type, recipient.key())) return;
        try {
            boolean delivered = Boolean.TRUE.equals(
                    emailService.sendEmail(recipient.email(), subject, body).join());
            if (delivered) {
                alertHistoryRepository.save(SlaAlertHistory.builder()
                        .incident(incident)
                        .alertType(type)
                        .recipientScope(recipient.scope())
                        .recipientKey(recipient.key())
                        .build());
            } else {
                log.warn("Chưa gửi được SLA alert {} cho {}; sẽ thử lại", type, recipient.email());
            }
        } catch (RuntimeException ex) {
            log.warn("Không thể gửi SLA alert cho {}", recipient.email(), ex);
        }
    }

    private record Recipient(String key, String email, String scope) {}
}
