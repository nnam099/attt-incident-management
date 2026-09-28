package com.attt.incident.service;

import com.attt.incident.entity.Incident;
import com.attt.incident.entity.IncidentLog;
import com.attt.incident.entity.User;
import com.attt.incident.repository.IncidentLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/** Appends tamper-evident incident audit records inside the caller's transaction. */
@Service
@RequiredArgsConstructor
public class IncidentAuditService {

    private final IncidentLogRepository logRepository;

    public IncidentLog append(Incident incident, User actor, String actionType,
                              String oldValue, String newValue, String note) {
        String previousHash = logRepository.findFirstByIncidentIdOrderByIdDesc(incident.getId())
                .map(IncidentLog::getRecordHash)
                .orElse("");
        LocalDateTime createdAt = LocalDateTime.now()
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        IncidentLog log = IncidentLog.builder()
                .incident(incident)
                .performedBy(actor)
                .actionType(actionType)
                .oldValue(oldValue)
                .newValue(newValue)
                .note(note)
                .previousHash(previousHash.isBlank() ? null : previousHash)
                .createdAt(createdAt)
                .recordHash(hash(previousHash, incident.getId(), actor.getId(), actionType,
                        oldValue, newValue, note, createdAt))
                .build();
        return logRepository.save(log);
    }

    private String hash(String previousHash, Long incidentId, Long actorId, String actionType,
                        String oldValue, String newValue, String note, LocalDateTime createdAt) {
        String payload = String.join("|", previousHash, String.valueOf(incidentId),
                String.valueOf(actorId), String.valueOf(actionType), String.valueOf(oldValue),
                String.valueOf(newValue), String.valueOf(note), String.valueOf(createdAt));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 không khả dụng", ex);
        }
    }
}
