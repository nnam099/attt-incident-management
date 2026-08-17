package com.attt.incident.dto;

import com.attt.incident.entity.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class IncidentCreateRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    @NotBlank(message = "Mô tả không được để trống")
    private String description;

    private String affectedSystem;

    @NotNull(message = "Vui lòng chọn loại sự cố")
    private Long categoryId;

    // Nếu người khai báo không chắc, có thể để null -> hệ thống gợi ý theo category
    private IncidentSeverity severity;

    private LocalDateTime detectedAt;
    
    // Chỉ dùng cho script seed dữ liệu mẫu
    private LocalDateTime createdAt;
}
