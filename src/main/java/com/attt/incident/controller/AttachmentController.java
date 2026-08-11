package com.attt.incident.controller;

import com.attt.incident.dto.AttachmentResponse;
import com.attt.incident.entity.IncidentAttachment;
import com.attt.incident.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(value = "/incidents/{incidentId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> uploadFile(
            @PathVariable Long incidentId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        
        AttachmentResponse response = attachmentService.uploadFile(incidentId, file, authentication);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/incidents/{incidentId}/attachments")
    public ResponseEntity<List<AttachmentResponse>> getAttachmentsByIncident(
            @PathVariable Long incidentId,
            Authentication authentication) {
        
        List<AttachmentResponse> responses = attachmentService.getAttachmentsByIncident(incidentId, authentication);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long attachmentId,
            Authentication authentication) {
        
        Resource resource = attachmentService.downloadFile(attachmentId, authentication);
        IncidentAttachment metadata = attachmentService.getAttachmentMetadata(attachmentId);

        // Encode file name để hỗ trợ tiếng Việt
        String encodedFileName = URLEncoder.encode(metadata.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(resource);
    }
}
