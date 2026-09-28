package com.attt.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskRequest {
    @NotBlank(message = "Tên task không được để trống")
    @Size(max = 255, message = "Tên task không được vượt quá 255 ký tự")
    private String taskName;
}
