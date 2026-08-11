package com.attt.incident.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IncidentUpdateRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    @NotBlank(message = "Mô tả không được để trống")
    private String description;
}
