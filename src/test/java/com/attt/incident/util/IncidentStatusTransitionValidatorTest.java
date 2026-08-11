package com.attt.incident.util;

import com.attt.incident.entity.IncidentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentStatusTransitionValidatorTest {

    @Test
    void testValidTransitions() {
        assertTrue(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.NEW, IncidentStatus.IN_PROGRESS));
        assertTrue(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED));
        assertTrue(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.RESOLVED, IncidentStatus.CLOSED));
    }

    @Test
    void testInvalidTransitions() {
        // Không thể nhảy cóc từ NEW sang CLOSED
        assertFalse(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.NEW, IncidentStatus.CLOSED));
        // Không thể đóng khi đang IN_PROGRESS
        assertFalse(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.IN_PROGRESS, IncidentStatus.CLOSED));
        // Đã CLOSED thì không thể về NEW
        assertFalse(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.CLOSED, IncidentStatus.NEW));
    }
}
