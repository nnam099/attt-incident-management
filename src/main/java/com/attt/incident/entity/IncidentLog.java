package com.attt.incident.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Nhật ký xử lý sự cố (audit trail).
 * Bản ghi log KHÔNG được sửa/xóa sau khi tạo (chỉ INSERT) để đảm bảo
 * tính toàn vẹn - phục vụ điều tra và truy vết trách nhiệm.
 */
@Entity
@Table(name = "incident_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by", nullable = false)
    private User performedBy;

    // VD: "STATUS_CHANGE", "ASSIGNMENT", "SEVERITY_CHANGE", "COMMENT", "CREATE"
    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "old_value", length = 255)
    private String oldValue;

    @Column(name = "new_value", length = 255)
    private String newValue;

    @Column(columnDefinition = "TEXT")
    private String note;

    // Hash của bản ghi trước đó để tạo chuỗi liên kết (hash chain),
    // giúp phát hiện nếu dữ liệu log bị chỉnh sửa trái phép.
    @Column(name = "record_hash", length = 128)
    private String recordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
