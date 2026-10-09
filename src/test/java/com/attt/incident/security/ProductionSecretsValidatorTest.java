package com.attt.incident.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecretsValidatorTest {

    private static final String STRONG_JWT_SECRET =
            "a-production-only-jwt-secret-that-is-long-enough";
    private static final String STRONG_ENCRYPTION_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789ABCDEF".getBytes(StandardCharsets.UTF_8));

    @Test
    void acceptsIndependentStrongSecrets() {
        ProductionSecretsValidator validator = validator(STRONG_JWT_SECRET, STRONG_ENCRYPTION_KEY);

        assertThatCode(validator::run).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingEncryptionKeyWithActionableMessage() {
        ProductionSecretsValidator validator = validator(STRONG_JWT_SECRET, null);

        assertThatThrownBy(validator::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY")
                .hasMessageContaining("trống");
    }

    @Test
    void rejectsMalformedEncryptionKey() {
        ProductionSecretsValidator validator = validator(STRONG_JWT_SECRET, "not-base64%%%");

        assertThatThrownBy(validator::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void rejectsDevelopmentEncryptionKey() {
        ProductionSecretsValidator validator = validator(
                STRONG_JWT_SECRET,
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

        assertThatThrownBy(validator::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AES-256");
    }

    @Test
    void rejectsShortOrPlaceholderJwtSecret() {
        assertThatThrownBy(() -> validator("short", STRONG_ENCRYPTION_KEY).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> validator(
                "change-this-secret-key-in-production-min-256-bits-long",
                STRONG_ENCRYPTION_KEY).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void requiresSmtpCredentialsWhenEmailIsEnabled() {
        ProductionSecretsValidator validator = validator(STRONG_JWT_SECRET, STRONG_ENCRYPTION_KEY);
        ReflectionTestUtils.setField(validator, "emailEnabled", true);
        ReflectionTestUtils.setField(validator, "mailUsername", "");
        ReflectionTestUtils.setField(validator, "mailPassword", "");

        assertThatThrownBy(validator::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_USERNAME")
                .hasMessageContaining("MAIL_PASSWORD");
    }

    private ProductionSecretsValidator validator(String jwtSecret, String encryptionKey) {
        ProductionSecretsValidator validator = new ProductionSecretsValidator();
        ReflectionTestUtils.setField(validator, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(validator, "encryptionKey", encryptionKey);
        return validator;
    }
}
