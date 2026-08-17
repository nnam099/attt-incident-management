package com.attt.incident.dto;

import com.attt.incident.entity.IoCType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class IoCResponse {
    private Long id;
    private IoCType type;
    private String value;
    private String description;
    private LocalDateTime createdAt;
}
