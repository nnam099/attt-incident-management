package com.attt.incident.entity;

/**
 * Mức độ nghiêm trọng của sự cố ATTT.
 * Dùng để xác định SLA và quyền truy cập thông tin.
 */
public enum IncidentSeverity {
    LOW,        // Thấp - ảnh hưởng nhỏ, không lan rộng
    MEDIUM,     // Trung bình - ảnh hưởng một phần hệ thống
    HIGH,       // Cao - ảnh hưởng nhiều hệ thống / dữ liệu nhạy cảm
    CRITICAL    // Nghiêm trọng - ảnh hưởng toàn hệ thống, cần xử lý khẩn cấp
}
