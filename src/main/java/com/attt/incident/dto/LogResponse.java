package com.attt.incident.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LogResponse {
    private Long id;
    private String actionType;
    private String oldValue;
    private String newValue;
    private String note;
    private String actor;
    private LocalDateTime timestamp;
}
