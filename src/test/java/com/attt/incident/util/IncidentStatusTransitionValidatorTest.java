package com.attt.incident.util;

import com.attt.incident.entity.IncidentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentStatusTransitionValidatorTest {

    @Test
    void testValidTransitions() {
        assertTrue(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.NEW, IncidentStatus.TRIAGE));
        assertTrue(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.NEW, IncidentStatus.CLOSED));
        assertTrue(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.TRIAGE, IncidentStatus.INVESTIGATING));
        assertTrue(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.INVESTIGATING, IncidentStatus.CONTAINED));
        assertTrue(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.CONTAINED, IncidentStatus.RECOVERED));
        assertTrue(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.RECOVERED, IncidentStatus.RESOLVED));
        assertTrue(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.RESOLVED, IncidentStatus.CLOSED));
        assertTrue(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.CLOSED, IncidentStatus.REOPENED));
        assertTrue(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.REOPENED, IncidentStatus.INVESTIGATING));
    }

    @Test
    void testInvalidTransitions() {
        // Không thể nhảy cóc từ NEW sang RESOLVED
        assertFalse(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.NEW, IncidentStatus.RESOLVED));
        // Không thể nhảy cóc từ NEW sang CONTAINED
        assertFalse(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.NEW, IncidentStatus.CONTAINED));
        // Không thể đóng khi đang INVESTIGATING
        assertFalse(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.INVESTIGATING, IncidentStatus.CLOSED));
        // Đã CLOSED thì không thể về NEW
        assertFalse(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.CLOSED, IncidentStatus.NEW));
        // Không thể tự chuyển sang chính nó
        assertFalse(IncidentStatusTransitionValidator.isValidTransition(IncidentStatus.TRIAGE, IncidentStatus.TRIAGE));
    }
}
