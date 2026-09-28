package com.attt.incident.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AdminRecoveryRunner#validatePasswordPolicy(String)}.
 *
 * These tests cover the password policy validation logic independently of
 * the Spring context, database, or file system.
 */
class AdminRecoveryRunnerPasswordPolicyTest {

    @Test
    @DisplayName("null password → fails policy")
    void nullPassword_FailsPolicy() {
        assertNotNull(AdminRecoveryRunner.validatePasswordPolicy(null));
    }

    @ParameterizedTest
    @DisplayName("Short passwords (< 12 chars) → fail policy")
    @ValueSource(strings = {"Short@1", "Ab@12345678", "A@1bcdefgh0"})  // all < 12 chars
    void shortPassword_FailsPolicy(String password) {
        String error = AdminRecoveryRunner.validatePasswordPolicy(password);
        assertNotNull(error, "Expected policy violation for: " + password);
        assertTrue(error.contains("12"), "Error should mention length: " + error);
    }

    @Test
    @DisplayName("No uppercase → fails policy")
    void noUppercase_FailsPolicy() {
        String error = AdminRecoveryRunner.validatePasswordPolicy("alllower@12345");
        assertNotNull(error);
        assertTrue(error.contains("uppercase"), error);
    }

    @Test
    @DisplayName("No lowercase → fails policy")
    void noLowercase_FailsPolicy() {
        String error = AdminRecoveryRunner.validatePasswordPolicy("ALLUPPER@12345");
        assertNotNull(error);
        assertTrue(error.contains("lowercase"), error);
    }

    @Test
    @DisplayName("No digit → fails policy")
    void noDigit_FailsPolicy() {
        String error = AdminRecoveryRunner.validatePasswordPolicy("NoDigitHere@abc");
        assertNotNull(error);
        assertTrue(error.contains("digit"), error);
    }

    @Test
    @DisplayName("No special character → fails policy")
    void noSpecialChar_FailsPolicy() {
        String error = AdminRecoveryRunner.validatePasswordPolicy("NoSpecial123456");
        assertNotNull(error);
        assertTrue(error.contains("special"), error);
    }

    @ParameterizedTest
    @DisplayName("Valid passwords that meet all policy requirements → pass")
    @ValueSource(strings = {
            "SecurePass@12345",
            "C0mpl3x!Password",
            "MyV3ryL0ng#PassPhrase",
            "Tr0ub4dor&3XTRA"
    })
    void validPassword_PassesPolicy(String password) {
        assertNull(AdminRecoveryRunner.validatePasswordPolicy(password),
                "Password should pass policy: " + password);
    }

    @Test
    @DisplayName("Exactly 12 chars meeting all requirements → passes")
    void exactly12Chars_WithAllRequirements_Passes() {
        String password = "Aa1!bbbbbbbb"; // 12 chars, upper, lower, digit, special
        assertNull(AdminRecoveryRunner.validatePasswordPolicy(password));
    }

    @Test
    @DisplayName("11 chars even with all character types → fails (length)")
    void exactly11Chars_Fails() {
        String password = "Aa1!bbbbbbb"; // 11 chars
        String error = AdminRecoveryRunner.validatePasswordPolicy(password);
        assertNotNull(error);
        assertTrue(error.contains("12"), error);
    }
}
