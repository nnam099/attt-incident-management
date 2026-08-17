package com.attt.incident.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class TaskResponse {
    private Long id;
    private String taskName;
    private boolean isCompleted;
    private LocalDateTime completedAt;
    private String completedByUsername;
}
