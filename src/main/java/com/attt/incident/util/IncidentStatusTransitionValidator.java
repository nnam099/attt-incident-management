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
        ALLOWED_TRANSITIONS.put(IncidentStatus.NEW, EnumSet.of(IncidentStatus.IN_PROGRESS));
        ALLOWED_TRANSITIONS.put(IncidentStatus.IN_PROGRESS, EnumSet.of(IncidentStatus.RESOLVED, IncidentStatus.NEW));
        ALLOWED_TRANSITIONS.put(IncidentStatus.RESOLVED, EnumSet.of(IncidentStatus.CLOSED, IncidentStatus.IN_PROGRESS));
        ALLOWED_TRANSITIONS.put(IncidentStatus.CLOSED, EnumSet.of(IncidentStatus.REOPENED));
        ALLOWED_TRANSITIONS.put(IncidentStatus.REOPENED, EnumSet.of(IncidentStatus.IN_PROGRESS));
    }

    public static boolean isValidTransition(IncidentStatus from, IncidentStatus to) {
        if (from == to) return false;
        Set<IncidentStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}
