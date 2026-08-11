package com.attt.incident.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Loại/nhóm sự cố: mã độc, tấn công mạng, rò rỉ dữ liệu, phishing,
 * truy cập trái phép, lỗi hệ thống, v.v.
 */
@Entity
@Table(name = "incident_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IncidentCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    // Mức độ mặc định gợi ý khi chọn loại này (có thể override khi khai báo)
    @Enumerated(EnumType.STRING)
    @Column(name = "default_severity", length = 20)
    private IncidentSeverity defaultSeverity;
}
