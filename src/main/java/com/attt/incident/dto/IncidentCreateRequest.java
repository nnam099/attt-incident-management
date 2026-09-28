package com.attt.incident.dto;

import com.attt.incident.entity.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class IncidentCreateRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    private String title;

    @NotBlank(message = "Mô tả không được để trống")
    @Size(max = 20_000, message = "Mô tả không được vượt quá 20000 ký tự")
    private String description;

    @Size(max = 255, message = "Hệ thống ảnh hưởng không được vượt quá 255 ký tự")
    private String affectedSystem;

    @NotNull(message = "Vui lòng chọn loại sự cố")
    private Long categoryId;

    // Nếu người khai báo không chắc, có thể để null -> hệ thống gợi ý theo category
    private IncidentSeverity severity;

    @PastOrPresent(message = "Thời gian phát hiện không được ở tương lai")
    private LocalDateTime detectedAt;
    
    // Chỉ dùng cho script seed dữ liệu mẫu
    @PastOrPresent(message = "Thời gian tạo không được ở tương lai")
    private LocalDateTime createdAt;
}
