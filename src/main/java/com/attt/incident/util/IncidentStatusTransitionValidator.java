package com.attt.incident.util;

import com.attt.incident.entity.IncidentStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Xác định các bước chuyển trạng thái hợp lệ trong quy trình xử lý sự cố.
 * NEW -> VERIFYING -> PROCESSING -> PENDING_CONFIRMATION -> CLOSED
 * CLOSED -> REOPENED -> PROCESSING
 */
public class IncidentStatusTransitionValidator {

    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(IncidentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(IncidentStatus.NEW, EnumSet.of(IncidentStatus.VERIFYING));
        ALLOWED_TRANSITIONS.put(IncidentStatus.VERIFYING, EnumSet.of(IncidentStatus.PROCESSING, IncidentStatus.NEW));
        ALLOWED_TRANSITIONS.put(IncidentStatus.PROCESSING, EnumSet.of(IncidentStatus.PENDING_CONFIRMATION));
        ALLOWED_TRANSITIONS.put(IncidentStatus.PENDING_CONFIRMATION, EnumSet.of(IncidentStatus.CLOSED, IncidentStatus.PROCESSING));
        ALLOWED_TRANSITIONS.put(IncidentStatus.CLOSED, EnumSet.of(IncidentStatus.REOPENED));
        ALLOWED_TRANSITIONS.put(IncidentStatus.REOPENED, EnumSet.of(IncidentStatus.PROCESSING));
    }

    public static boolean isValidTransition(IncidentStatus from, IncidentStatus to) {
        if (from == to) return false;
        Set<IncidentStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}
