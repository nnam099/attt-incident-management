package com.attt.incident.entity;

/**
 * Trạng thái xử lý sự cố (workflow).
 * Luồng hợp lệ:
 * NEW -> VERIFYING -> PROCESSING -> PENDING_CONFIRMATION -> CLOSED
 * CLOSED -> REOPENED -> PROCESSING (nếu sự cố tái diễn)
 */
public enum IncidentStatus {
    NEW,                    // Mới tiếp nhận
    VERIFYING,              // Đang xác minh
    PROCESSING,             // Đang xử lý
    PENDING_CONFIRMATION,   // Chờ xác nhận hoàn tất
    CLOSED,                 // Đã đóng
    REOPENED                // Tái mở
}
