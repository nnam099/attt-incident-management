package com.attt.incident.dto;

public record AuditIntegrityResponse(
        boolean valid,
        int checkedRecords,
        Long firstInvalidLogId
) {}
