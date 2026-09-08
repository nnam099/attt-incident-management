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
            if (incident.getAssignedTo() != null) emailService.sendEmail(incident.getAssignedTo().getEmail(), subject, body);
            if (type.name().startsWith("ACK_")) userRepository.findByRoleName(RoleName.HELPDESK).forEach(u -> emailService.sendEmail(u.getEmail(), subject, body));
            if (escalate) userRepository.findByRoleName(RoleName.MANAGER).forEach(u -> emailService.sendEmail(u.getEmail(), subject, body));
            alertHistoryRepository.save(SlaAlertHistory.builder().incident(incident).alertType(type)
                    .recipientScope(escalate ? "ESCALATED" : "OPERATIONAL").build());
        }
    }
}
