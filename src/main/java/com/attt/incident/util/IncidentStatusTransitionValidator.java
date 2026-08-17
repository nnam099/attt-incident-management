package com.attt.incident.util;

import com.attt.incident.entity.IncidentStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Xác định các bước chuyển trạng thái hợp lệ trong quy trình xử lý sự cố.
 * NEW -> IN_PROGRESS -> RESOLVED -> CLOSED
 * CLOSED -> REOPENED -> IN_PROGRESS
 */
public class IncidentStatusTransitionValidator {

    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(IncidentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(IncidentStatus.NEW, EnumSet.of(IncidentStatus.TRIAGE, IncidentStatus.CLOSED)); // CLOSED for immediate False Positive
        ALLOWED_TRANSITIONS.put(IncidentStatus.TRIAGE, EnumSet.of(IncidentStatus.INVESTIGATING, IncidentStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(IncidentStatus.INVESTIGATING, EnumSet.of(IncidentStatus.CONTAINED, IncidentStatus.RECOVERED, IncidentStatus.RESOLVED));
        ALLOWED_TRANSITIONS.put(IncidentStatus.CONTAINED, EnumSet.of(IncidentStatus.RECOVERED, IncidentStatus.INVESTIGATING));
        ALLOWED_TRANSITIONS.put(IncidentStatus.RECOVERED, EnumSet.of(IncidentStatus.RESOLVED, IncidentStatus.INVESTIGATING));
        ALLOWED_TRANSITIONS.put(IncidentStatus.RESOLVED, EnumSet.of(IncidentStatus.CLOSED, IncidentStatus.INVESTIGATING));
        ALLOWED_TRANSITIONS.put(IncidentStatus.CLOSED, EnumSet.of(IncidentStatus.REOPENED));
        ALLOWED_TRANSITIONS.put(IncidentStatus.REOPENED, EnumSet.of(IncidentStatus.INVESTIGATING, IncidentStatus.TRIAGE));
    }

    public static boolean isValidTransition(IncidentStatus from, IncidentStatus to) {
        if (from == to) return false;
        Set<IncidentStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}
