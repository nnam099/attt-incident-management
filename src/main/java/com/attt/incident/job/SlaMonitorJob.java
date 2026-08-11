package com.attt.incident.job;

import com.attt.incident.entity.Incident;
import com.attt.incident.entity.RoleName;
import com.attt.incident.entity.User;
import com.attt.incident.repository.IncidentRepository;
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
    private final UserRepository userRepository;
    private final EmailService emailService;

    // Quét mỗi 15 phút (900000 ms)
    @Scheduled(fixedRate = 900000)
    @Transactional
    public void scanAndAlertSla() {
        log.info("Bắt đầu quét sự cố sắp vi phạm SLA...");

        // Cảnh báo trước 2 tiếng
        LocalDateTime thresholdTime = LocalDateTime.now().plusHours(2);
        
        List<Incident> atRiskIncidents = incidentRepository.findIncidentsApproachingSla(thresholdTime);
        
        if (atRiskIncidents.isEmpty()) {
            log.info("Không có sự cố nào sắp vi phạm SLA.");
            return;
        }

        List<User> managers = userRepository.findByRoleName(RoleName.MANAGER);

        for (Incident incident : atRiskIncidents) {
            String subject = "[CẢNH BÁO SLA] Sự cố " + incident.getIncidentCode() + " sắp quá hạn!";
            String body = String.format(
                    "Sự cố %s (%s) sắp quá hạn SLA vào lúc %s.\nTrạng thái hiện tại: %s.\nVui lòng xử lý gấp!",
                    incident.getIncidentCode(),
                    incident.getTitle(),
                    incident.getSlaDueAt(),
                    incident.getStatus()
            );

            // 1. Gửi cho người được phân công
            if (incident.getAssignedTo() != null && incident.getAssignedTo().getEmail() != null) {
                emailService.sendEmail(incident.getAssignedTo().getEmail(), subject, body);
            }

            // 2. Gửi cho toàn bộ MANAGER
            for (User manager : managers) {
                if (manager.getEmail() != null) {
                    emailService.sendEmail(manager.getEmail(), subject, body);
                }
            }

            // Đánh dấu đã gửi cảnh báo để không gửi lặp lại ở chu kỳ sau
            incident.setSlaWarningSent(true);
            incidentRepository.save(incident);
            
            log.info("Đã gửi cảnh báo SLA cho sự cố {}", incident.getIncidentCode());
        }
        
        log.info("Hoàn tất quét SLA.");
    }
}
