package com.attt.incident.entity;

/**
 * Trạng thái xử lý sự cố (workflow).
 * Luồng hợp lệ:
 * Luồng hợp lệ:
 * NEW -> IN_PROGRESS -> RESOLVED -> CLOSED
 * CLOSED -> REOPENED -> IN_PROGRESS (nếu sự cố tái diễn)
 */
public enum IncidentStatus {
    NEW,                    // Mới tiếp nhận (Tự động hoặc User report)
    TRIAGE,                 // Phân loại ban đầu (Tier 1 xử lý True/False Positive)
    INVESTIGATING,          // Đang điều tra (Tier 2 tiếp nhận)
    CONTAINED,              // Đã ngăn chặn
    RECOVERED,              // Đã khôi phục
    RESOLVED,               // Đã giải quyết xong (Viết Post-mortem)
    CLOSED,                 // Đã đóng sự cố (Manager xác nhận)
    REOPENED                // Tái mở
}
