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
    private final com.attt.incident.repository.IncidentRepository incidentRepository;

    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public IncidentLog append(Incident incident, User actor, String actionType,
                              String oldValue, String newValue, String note) {
        incidentRepository.findByIdForUpdate(incident.getId())
                .orElseThrow(() -> new com.attt.incident.exception.ResourceNotFoundException(
                        "Không tìm thấy sự cố với id: " + incident.getId()));
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

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public IntegrityResult verify(Long incidentId) {
        String previousHash = "";
        int checked = 0;
        for (IncidentLog log : logRepository.findByIncidentIdOrderByIdAsc(incidentId)) {
            String storedPrevious = log.getPreviousHash() == null ? "" : log.getPreviousHash();
            String expected = hash(previousHash, incidentId,
                    log.getPerformedBy() == null ? null : log.getPerformedBy().getId(),
                    log.getActionType(), log.getOldValue(), log.getNewValue(),
                    log.getNote(), log.getCreatedAt());
            checked++;
            if (!java.util.Objects.equals(previousHash, storedPrevious)
                    || !java.util.Objects.equals(expected, log.getRecordHash())) {
                return new IntegrityResult(false, checked, log.getId());
            }
            previousHash = log.getRecordHash();
        }
        return new IntegrityResult(true, checked, null);
    }

    public record IntegrityResult(boolean valid, int checkedRecords, Long firstInvalidLogId) {}

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
