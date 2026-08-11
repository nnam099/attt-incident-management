package com.attt.incident.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignRequest {

    @NotNull(message = "Vui lòng chọn người xử lý")
    private Long assigneeUserId;

    private String note;
}
