package com.attt.incident.dto;

import com.attt.incident.entity.IncidentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatusUpdateRequest {

    @NotNull(message = "Trạng thái mới không được để trống")
    private IncidentStatus newStatus;

    @Size(max = 2_000, message = "Ghi chú không được vượt quá 2000 ký tự")
    private String note;
    
    private com.attt.incident.entity.ResolutionType resolutionType;
}
