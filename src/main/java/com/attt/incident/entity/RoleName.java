package com.attt.incident.entity;

/**
 * Vai trò người dùng trong hệ thống.
 */
public enum RoleName {
    ADMIN,          // Quản trị hệ thống
    MANAGER,        // Quản lý / trưởng nhóm ATTT - phân công, phê duyệt
    HELPDESK,       // Cán bộ tiếp nhận sự cố
    ANALYST,        // Chuyên viên xử lý sự cố
    REPORTER        // Người dùng khai báo sự cố
}
