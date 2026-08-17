package com.attt.incident.dto;

import com.attt.incident.entity.IoCType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IoCRequest {
    @NotNull(message = "Loại IoC không được để trống")
    private IoCType type;

    @NotBlank(message = "Giá trị IoC không được để trống")
    private String value;

    private String description;
}
