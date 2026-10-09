package com.attt.incident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("isCompleted")
    private boolean isCompleted;
    private LocalDateTime completedAt;
    private String completedByUsername;
}
