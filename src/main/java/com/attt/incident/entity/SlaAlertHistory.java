package com.attt.incident.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sla_alert_history", uniqueConstraints = @UniqueConstraint(columnNames = {"incident_id", "alert_type"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SlaAlertHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30)
    private SlaAlertType alertType;

    @Column(name = "recipient_scope", nullable = false, length = 30)
    private String recipientScope;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }
}
