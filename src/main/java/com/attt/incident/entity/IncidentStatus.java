package com.attt.incident.entity;

/**
 * Trạng thái xử lý sự cố (workflow).
 * Luồng hợp lệ:
 * Luồng hợp lệ:
 * NEW -> IN_PROGRESS -> RESOLVED -> CLOSED
 * CLOSED -> REOPENED -> IN_PROGRESS (nếu sự cố tái diễn)
 */
public enum IncidentStatus {
    NEW,                    // Mới tiếp nhận
    IN_PROGRESS,            // Đang xử lý
    RESOLVED,               // Đã giải quyết
    CLOSED,                 // Đã đóng
    REOPENED                // Tái mở
}
