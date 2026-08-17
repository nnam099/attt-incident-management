package com.attt.incident.service;

import com.attt.incident.dto.AssignRequest;
import com.attt.incident.dto.IncidentCreateRequest;
import com.attt.incident.dto.IncidentResponse;
import com.attt.incident.dto.StatusUpdateRequest;
import com.attt.incident.entity.*;
import com.attt.incident.exception.InvalidStatusTransitionException;
import com.attt.incident.exception.ResourceNotFoundException;
import com.attt.incident.repository.*;
import com.attt.incident.util.IncidentStatusTransitionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentCategoryRepository categoryRepository;
    private final IncidentLogRepository logRepository;
    private final UserRepository userRepository;
    private final IoCRepository iocRepository;
    private final IncidentTaskRepository taskRepository;
    private final EmailService emailService;
    private final WebSocketNotificationService wsNotificationService;

    // SLA mặc định theo mức độ (giờ) - có thể chuyển sang bảng cấu hình sau
    private static final Map<IncidentSeverity, Integer> SLA_HOURS = Map.of(
            IncidentSeverity.LOW, 72,
            IncidentSeverity.MEDIUM, 48,
            IncidentSeverity.HIGH, 24,
            IncidentSeverity.CRITICAL, 4
    );

    @Transactional
    public IncidentResponse createIncident(IncidentCreateRequest request, Authentication auth) {
        User reporter = getCurrentUser(auth);

        IncidentCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại sự cố"));

        IncidentSeverity severity = request.getSeverity() != null
                ? request.getSeverity()
                : category.getDefaultSeverity() != null ? category.getDefaultSeverity() : IncidentSeverity.MEDIUM;

        int resolveHours = SLA_HOURS.getOrDefault(severity, 48);
        int ackHours = Math.max(1, resolveHours / 4); // VD: Resolve 24h -> Ack 6h

        LocalDateTime baseTime = request.getCreatedAt() != null ? request.getCreatedAt() : LocalDateTime.now();

        Incident incident = Incident.builder()
                .incidentCode(generateIncidentCode())
                .title(request.getTitle())
                .description(request.getDescription())
                .affectedSystem(request.getAffectedSystem())
                .category(category)
                .severity(severity)
                .status(IncidentStatus.NEW)
                .reportedBy(reporter)
                .detectedAt(request.getDetectedAt() != null ? request.getDetectedAt() : baseTime)
                .ackDueAt(baseTime.plusHours(ackHours))
                .resolveDueAt(baseTime.plusHours(resolveHours))
                .createdAt(baseTime)
                .updatedAt(baseTime)
                .build();

        incident = incidentRepository.save(incident);

        writeLog(incident, reporter, "CREATE", null, IncidentStatus.NEW.name(), "Khai báo sự cố mới");

        sendNewIncidentEmails(incident, reporter);

        IncidentResponse response = toResponse(incident, auth);
        wsNotificationService.notifyIncidentUpdate(response);

        return response;
    }

    private void sendNewIncidentEmails(Incident incident, User reporter) {
        // 1. Gửi email xác nhận cho người báo cáo
        if (reporter.getEmail() != null) {
            String subject = "[Xác nhận] Đã tiếp nhận khai báo sự cố: " + incident.getIncidentCode();
            String body = String.format("Chào %s,\n\nHệ thống đã tiếp nhận sự cố '%s' của bạn.\nMã sự cố: %s.\nChúng tôi sẽ xử lý trong thời gian sớm nhất.",
                    reporter.getFullName() != null ? reporter.getFullName() : reporter.getUsername(),
                    incident.getTitle(),
                    incident.getIncidentCode());
            emailService.sendEmail(reporter.getEmail(), subject, body);
        }

        // 2. Thông báo cho đội HELPDESK
        java.util.List<User> helpdesks = userRepository.findByRoleName(RoleName.HELPDESK);
        String hdSubject = "[Sự cố mới] " + incident.getIncidentCode() + " cần được xử lý";
        String hdBody = String.format("Sự cố mới đã được khai báo bởi %s.\nMã: %s\nTiêu đề: %s\nVui lòng đăng nhập hệ thống để tiếp nhận và phân công.",
                reporter.getUsername(), incident.getIncidentCode(), incident.getTitle());
        
        for (User hd : helpdesks) {
            if (hd.getEmail() != null) {
                emailService.sendEmail(hd.getEmail(), hdSubject, hdBody);
            }
        }
    }

    @Transactional
    public IncidentResponse changeStatus(Long incidentId, StatusUpdateRequest request, Authentication auth) {
        Incident incident = getIncidentOrThrow(incidentId);
        User actor = getCurrentUser(auth);

        IncidentStatus oldStatus = incident.getStatus();
        IncidentStatus newStatus = request.getNewStatus();

        if (!IncidentStatusTransitionValidator.isValidTransition(oldStatus, newStatus)) {
            throw new InvalidStatusTransitionException(
                    String.format("Không thể chuyển trạng thái từ %s sang %s", oldStatus, newStatus));
        }

        incident.setStatus(newStatus);
        
        // Cập nhật AcknowledgedAt nếu chuyển sang TRIAGE hoặc INVESTIGATING
        if ((newStatus == IncidentStatus.TRIAGE || newStatus == IncidentStatus.INVESTIGATING) 
                && incident.getAcknowledgedAt() == null) {
            incident.setAcknowledgedAt(LocalDateTime.now());
        }

        if (newStatus == IncidentStatus.CLOSED) {
            incident.setClosedAt(LocalDateTime.now());
            if (request.getResolutionType() != null) {
                incident.setResolutionType(request.getResolutionType());
            }
        }
        incidentRepository.save(incident);

        writeLog(incident, actor, "STATUS_CHANGE", oldStatus.name(), newStatus.name(), request.getNote());

        IncidentResponse response = toResponse(incident, auth);
        wsNotificationService.notifyIncidentUpdate(response);

        return response;
    }

    @Transactional
    public IncidentResponse assignIncident(Long incidentId, AssignRequest request, Authentication auth) {
        Incident incident = getIncidentOrThrow(incidentId);
        User actor = getCurrentUser(auth);

        User assignee = userRepository.findById(request.getAssigneeUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người xử lý được chọn"));

        String oldAssignee = incident.getAssignedTo() != null ? incident.getAssignedTo().getUsername() : "chưa phân công";
        incident.setAssignedTo(assignee);
        incidentRepository.save(incident);

        writeLog(incident, actor, "ASSIGNMENT", oldAssignee, assignee.getUsername(), request.getNote());

        IncidentResponse response = toResponse(incident, auth);
        wsNotificationService.notifyIncidentUpdate(response);
        wsNotificationService.notifyUserAssignment(assignee.getUsername(), response);

        return response;
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(Long incidentId, Authentication auth) {
        Incident incident = getIncidentOrThrow(incidentId);
        checkViewPermission(incident, auth);
        return toResponse(incident, auth);
    }

    private Incident getIncidentOrThrow(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố với id: " + id));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<IncidentResponse> getIncidents(
            IncidentStatus status, IncidentSeverity severity, Long assigneeId,
            org.springframework.data.domain.Pageable pageable, Authentication auth) {
        
        org.springframework.data.jpa.domain.Specification<Incident> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (assigneeId != null) {
                predicates.add(cb.equal(root.join("assignedTo").get("id"), assigneeId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return incidentRepository.findAll(spec, pageable)
                .map(incident -> toResponse(incident, auth));
    }

    @Transactional
    public IncidentResponse updateIncident(Long incidentId, com.attt.incident.dto.IncidentUpdateRequest request, Authentication auth) {
        Incident incident = getIncidentOrThrow(incidentId);
        if (!hasFullAccess(incident, auth)) {
            throw new AccessDeniedException("Bạn không có quyền chỉnh sửa sự cố này");
        }

        User actor = getCurrentUser(auth);
        String oldTitle = incident.getTitle();
        String oldDesc = incident.getDescription();
        
        incident.setTitle(request.getTitle());
        incident.setDescription(request.getDescription());
        incident = incidentRepository.save(incident);

        writeLog(incident, actor, "UPDATE", oldTitle, request.getTitle(), "Cập nhật thông tin sự cố");

        IncidentResponse response = toResponse(incident, auth);
        wsNotificationService.notifyIncidentUpdate(response);
        return response;
    }

    @Transactional(readOnly = true)
    public java.util.List<com.attt.incident.dto.LogResponse> getIncidentLogs(Long incidentId, Authentication auth) {
        Incident incident = getIncidentOrThrow(incidentId);
        checkViewPermission(incident, auth);

        return incident.getLogs().stream()
                .map(log -> com.attt.incident.dto.LogResponse.builder()
                        .id(log.getId())
                        .actionType(log.getActionType())
                        .oldValue(log.getOldValue())
                        .newValue(log.getNewValue())
                        .note(log.getNote())
                        .actor(log.getPerformedBy() != null ? log.getPerformedBy().getUsername() : "Hệ thống")
                        .timestamp(log.getCreatedAt())
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public com.attt.incident.dto.LogResponse addComment(Long incidentId, com.attt.incident.dto.CommentRequest request, Authentication auth) {
        Incident incident = getIncidentOrThrow(incidentId);
        if (!hasFullAccess(incident, auth)) {
            throw new AccessDeniedException("Bạn không có quyền bình luận trên sự cố này");
        }

        User actor = getCurrentUser(auth);
        
        IncidentLog log = IncidentLog.builder()
                .incident(incident)
                .performedBy(actor)
                .actionType("COMMENT")
                .note(request.getContent())
                .build();
        log = logRepository.save(log);

        return com.attt.incident.dto.LogResponse.builder()
                .id(log.getId())
                .actionType(log.getActionType())
                .note(log.getNote())
                .actor(actor.getUsername())
                .timestamp(log.getCreatedAt())
                .build();
    }

    @Transactional
    public com.attt.incident.dto.IoCResponse addIoC(Long incidentId, com.attt.incident.dto.IoCRequest request, Authentication auth) {
        Incident incident = getIncidentOrThrow(incidentId);
        if (!hasFullAccess(incident, auth)) {
            throw new AccessDeniedException("Bạn không có quyền thêm IoC");
        }

        IoC ioc = IoC.builder()
                .incident(incident)
                .type(request.getType())
                .value(request.getValue())
                .description(request.getDescription())
                .build();
        
        ioc = iocRepository.save(ioc);

        writeLog(incident, getCurrentUser(auth), "ADD_IOC", null, ioc.getValue(), "Thêm IoC mới: " + ioc.getType());

        return com.attt.incident.dto.IoCResponse.builder()
                .id(ioc.getId())
                .type(ioc.getType())
                .value(ioc.getValue())
                .description(ioc.getDescription())
                .createdAt(ioc.getCreatedAt())
                .build();
    }

    @Transactional
    public void deleteIoC(Long incidentId, Long iocId, Authentication auth) {
        Incident incident = getIncidentOrThrow(incidentId);
        if (!hasFullAccess(incident, auth)) {
            throw new AccessDeniedException("Bạn không có quyền xóa IoC");
        }

        IoC ioc = iocRepository.findById(iocId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy IoC"));

        if (!ioc.getIncident().getId().equals(incident.getId())) {
            throw new IllegalArgumentException("IoC không thuộc về sự cố này");
        }

        iocRepository.delete(ioc);
        writeLog(incident, getCurrentUser(auth), "DELETE_IOC", ioc.getValue(), null, "Xóa IoC: " + ioc.getType());
    }

    @Transactional
    public com.attt.incident.dto.TaskResponse addTask(Long incidentId, com.attt.incident.dto.TaskRequest request, Authentication auth) {
        Incident incident = getIncidentOrThrow(incidentId);
        if (!hasFullAccess(incident, auth)) {
            throw new AccessDeniedException("Bạn không có quyền thêm Task");
        }

        IncidentTask task = IncidentTask.builder()
                .incident(incident)
                .taskName(request.getTaskName())
                .isCompleted(false)
                .build();

        task = taskRepository.save(task);

        writeLog(incident, getCurrentUser(auth), "ADD_TASK", null, task.getTaskName(), "Thêm nhiệm vụ mới");

        return com.attt.incident.dto.TaskResponse.builder()
                .id(task.getId())
                .taskName(task.getTaskName())
                .isCompleted(task.isCompleted())
                .build();
    }

    @Transactional
    public com.attt.incident.dto.TaskResponse toggleTask(Long incidentId, Long taskId, Authentication auth) {
        Incident incident = getIncidentOrThrow(incidentId);
        if (!hasFullAccess(incident, auth)) {
            throw new AccessDeniedException("Bạn không có quyền cập nhật Task");
        }
        User actor = getCurrentUser(auth);

        IncidentTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Task"));

        if (!task.getIncident().getId().equals(incident.getId())) {
            throw new IllegalArgumentException("Task không thuộc về sự cố này");
        }

        task.setCompleted(!task.isCompleted());
        if (task.isCompleted()) {
            task.setCompletedAt(LocalDateTime.now());
            task.setCompletedBy(actor);
        } else {
            task.setCompletedAt(null);
            task.setCompletedBy(null);
        }

        task = taskRepository.save(task);

        writeLog(incident, actor, "UPDATE_TASK", String.valueOf(!task.isCompleted()), String.valueOf(task.isCompleted()), "Cập nhật trạng thái nhiệm vụ: " + task.getTaskName());

        return com.attt.incident.dto.TaskResponse.builder()
                .id(task.getId())
                .taskName(task.getTaskName())
                .isCompleted(task.isCompleted())
                .completedAt(task.getCompletedAt())
                .completedByUsername(task.getCompletedBy() != null ? task.getCompletedBy().getUsername() : null)
                .build();
    }

    /**
     * Kiểm soát truy cập: người khai báo, người được phân công, MANAGER, ADMIN
     * được xem chi tiết. Các vai trò khác chỉ thấy thông tin tổng quan (che mô tả).
     */
    private void checkViewPermission(Incident incident, Authentication auth) {
        // Việc chặn hoàn toàn có thể áp dụng cho case cực nhạy cảm; ở đây minh họa
        // bằng cách che dữ liệu trong toResponse() thay vì chặn truy cập hẳn.
    }

    private boolean hasFullAccess(Incident incident, Authentication auth) {
        User current = getCurrentUser(auth);
        boolean isOwnerOrAssignee = incident.getReportedBy() != null && incident.getReportedBy().getId().equals(current.getId())
                || incident.getAssignedTo() != null && incident.getAssignedTo().getId().equals(current.getId());

        boolean isPrivileged = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_MANAGER"));

        return isOwnerOrAssignee || isPrivileged;
    }

    private IncidentResponse toResponse(Incident incident, Authentication auth) {
        boolean fullAccess = hasFullAccess(incident, auth);

        return IncidentResponse.builder()
                .id(incident.getId())
                .incidentCode(incident.getIncidentCode())
                .title(incident.getTitle())
                .description(fullAccess ? incident.getDescription() : "[Nội dung bị hạn chế truy cập]")
                .affectedSystem(incident.getAffectedSystem())
                .categoryName(incident.getCategory() != null ? incident.getCategory().getName() : null)
                .severity(incident.getSeverity())
                .status(incident.getStatus())
                .reportedByUsername(incident.getReportedBy() != null ? incident.getReportedBy().getUsername() : null)
                .assignedToUsername(incident.getAssignedTo() != null ? incident.getAssignedTo().getUsername() : null)
                .detectedAt(incident.getDetectedAt())
                .ackDueAt(incident.getAckDueAt())
                .acknowledgedAt(incident.getAcknowledgedAt())
                .resolveDueAt(incident.getResolveDueAt())
                .resolutionType(incident.getResolutionType())
                .createdAt(incident.getCreatedAt())
                .updatedAt(incident.getUpdatedAt())
                .iocs(incident.getIocs() != null ? incident.getIocs().stream()
                        .map(ioc -> com.attt.incident.dto.IoCResponse.builder()
                                .id(ioc.getId())
                                .type(ioc.getType())
                                .value(ioc.getValue())
                                .description(ioc.getDescription())
                                .createdAt(ioc.getCreatedAt())
                                .build())
                        .collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList())
                .tasks(incident.getTasks() != null ? incident.getTasks().stream()
                        .map(task -> com.attt.incident.dto.TaskResponse.builder()
                                .id(task.getId())
                                .taskName(task.getTaskName())
                                .isCompleted(task.isCompleted())
                                .completedAt(task.getCompletedAt())
                                .completedByUsername(task.getCompletedBy() != null ? task.getCompletedBy().getUsername() : null)
                                .build())
                        .collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList())
                .build();
    }

    private void writeLog(Incident incident, User actor, String actionType, String oldValue, String newValue, String note) {
        IncidentLog log = IncidentLog.builder()
                .incident(incident)
                .performedBy(actor)
                .actionType(actionType)
                .oldValue(oldValue)
                .newValue(newValue)
                .note(note)
                .build();
        logRepository.save(log);
    }

    private synchronized String generateIncidentCode() {
        String year = String.valueOf(Year.now().getValue());
        String prefix = "INC-" + year + "-";
        long count = incidentRepository.countByCodePrefix(prefix) + 1;
        return prefix + String.format("%04d", count);
    }

    private User getCurrentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Người dùng không hợp lệ"));
    }
}
