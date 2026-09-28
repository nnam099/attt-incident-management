package com.attt.incident.dto;

import com.attt.incident.entity.IoCType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IoCRequest {
    @NotNull(message = "Loại IoC không được để trống")
    private IoCType type;

    @NotBlank(message = "Giá trị IoC không được để trống")
    @Size(max = 255, message = "Giá trị IoC không được vượt quá 255 ký tự")
    private String value;

    @Size(max = 2_000, message = "Mô tả IoC không được vượt quá 2000 ký tự")
    private String description;
}
