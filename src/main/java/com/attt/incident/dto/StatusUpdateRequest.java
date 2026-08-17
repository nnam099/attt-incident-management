package com.attt.incident.dto;

import com.attt.incident.entity.IncidentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatusUpdateRequest {

    @NotNull(message = "Trạng thái mới không được để trống")
    private IncidentStatus newStatus;

    private String note;
    
    private com.attt.incident.entity.ResolutionType resolutionType;
}
