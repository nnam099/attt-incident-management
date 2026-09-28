package com.attt.incident.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignRequest {

    @NotNull(message = "Vui lòng chọn người xử lý")
    private Long assigneeUserId;

    @Size(max = 2_000, message = "Ghi chú không được vượt quá 2000 ký tự")
    private String note;
}
