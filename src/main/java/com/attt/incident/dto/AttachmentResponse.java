package com.attt.incident.dto;

import com.attt.incident.entity.IncidentAttachment;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AttachmentResponse {
    private Long id;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String uploadedBy;
    private LocalDateTime uploadedAt;

    public static AttachmentResponse fromEntity(IncidentAttachment attachment) {
        return AttachmentResponse.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .uploadedBy(attachment.getUploadedBy() != null ? attachment.getUploadedBy().getUsername() : null)
                .uploadedAt(attachment.getUploadedAt())
                .build();
    }
}
