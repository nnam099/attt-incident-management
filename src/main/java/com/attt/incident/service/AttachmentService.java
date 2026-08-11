package com.attt.incident.service;

import com.attt.incident.dto.AttachmentResponse;
import com.attt.incident.entity.Incident;
import com.attt.incident.entity.IncidentAttachment;
import com.attt.incident.entity.IncidentLog;
import com.attt.incident.entity.User;
import com.attt.incident.exception.BadRequestException;
import com.attt.incident.exception.ResourceNotFoundException;
import com.attt.incident.repository.IncidentAttachmentRepository;
import com.attt.incident.repository.IncidentLogRepository;
import com.attt.incident.repository.IncidentRepository;
import com.attt.incident.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final IncidentRepository incidentRepository;
    private final IncidentAttachmentRepository attachmentRepository;
    private final IncidentLogRepository logRepository;
    private final UserRepository userRepository;

    // Định nghĩa thư mục lưu trữ, có thể lấy từ application.properties
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg",
            "image/png",
            "application/pdf",
            "text/plain"
    );

    @Transactional
    public AttachmentResponse uploadFile(Long incidentId, MultipartFile file, Authentication auth) {
        if (file.isEmpty()) {
            throw new BadRequestException("File tải lên không được để trống");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("Kích thước file vượt quá giới hạn 10MB");
        }

        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BadRequestException("Định dạng file không được hỗ trợ. Vui lòng tải lên ảnh (JPG, PNG), PDF hoặc TXT");
        }

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố với id: " + incidentId));

        // Kiểm tra quyền upload (chỉ người liên quan hoặc ADMIN/MANAGER)
        if (!hasFullAccess(incident, auth)) {
            throw new AccessDeniedException("Bạn không có quyền đính kèm file cho sự cố này");
        }

        User uploader = getCurrentUser(auth);

        try {
            // Tạo thư mục nếu chưa tồn tại
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.lastIndexOf(".") > 0) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // Sinh tên file ngẫu nhiên để tránh đè
            String storedFileName = UUID.randomUUID().toString() + extension;
            Path filePath = uploadPath.resolve(storedFileName);

            // Lưu file xuống đĩa cứng local
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Lưu thông tin vào database
            IncidentAttachment attachment = IncidentAttachment.builder()
                    .incident(incident)
                    .fileName(originalFilename)
                    .storagePath(filePath.toString())
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .uploadedBy(uploader)
                    .build();

            attachment = attachmentRepository.save(attachment);

            // Ghi log
            writeLog(incident, uploader, "ATTACHMENT_ADDED", null, originalFilename, "Đính kèm file: " + originalFilename);

            return AttachmentResponse.fromEntity(attachment);
        } catch (IOException ex) {
            throw new RuntimeException("Lỗi khi lưu file: " + ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public Resource downloadFile(Long attachmentId, Authentication auth) {
        IncidentAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy file đính kèm với id: " + attachmentId));

        Incident incident = attachment.getIncident();

        // Kiểm tra quyền tải file
        if (!hasFullAccess(incident, auth)) {
            throw new AccessDeniedException("Bạn không có quyền tải file đính kèm của sự cố này");
        }

        try {
            Path filePath = Paths.get(attachment.getStoragePath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Không thể đọc được file đính kèm");
            }
        } catch (Exception ex) {
            throw new RuntimeException("Lỗi khi tải file đính kèm: " + ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public IncidentAttachment getAttachmentMetadata(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy file đính kèm với id: " + attachmentId));
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> getAttachmentsByIncident(Long incidentId, Authentication auth) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố với id: " + incidentId));
        
        if (!hasFullAccess(incident, auth)) {
            throw new AccessDeniedException("Bạn không có quyền xem file đính kèm của sự cố này");
        }
        
        return attachmentRepository.findByIncidentId(incidentId).stream()
                .map(AttachmentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    private boolean hasFullAccess(Incident incident, Authentication auth) {
        User current = getCurrentUser(auth);
        boolean isOwnerOrAssignee = (incident.getReportedBy() != null && incident.getReportedBy().getId().equals(current.getId()))
                || (incident.getAssignedTo() != null && incident.getAssignedTo().getId().equals(current.getId()));

        boolean isPrivileged = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_MANAGER"));

        return isOwnerOrAssignee || isPrivileged;
    }

    private User getCurrentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Người dùng không hợp lệ"));
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
}
