package com.attt.incident.dto;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private String fullName;
    private String department;
    private Boolean enabled;
}
