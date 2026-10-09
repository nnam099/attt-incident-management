package com.attt.incident.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Fails production startup before serving traffic when cryptographic secrets are unsafe. */
@Component
@Profile("prod")
@Order(0)
public class ProductionSecretsValidator implements CommandLineRunner {

    private static final String DEVELOPMENT_ENCRYPTION_KEY =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.encryption.key:}")
    private String encryptionKey;

    @Value("${app.notifications.email-enabled:false}")
    private boolean emailEnabled;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Override
    public void run(String... args) {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32
                || looksLikePlaceholder(jwtSecret)) {
            throw new IllegalStateException(
                    "JWT_SECRET phải là bí mật ngẫu nhiên riêng, tối thiểu 32 byte");
        }

        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY không được để trống");
        }

        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(encryptionKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY phải là Base64 hợp lệ", ex);
        }
        if (decodedKey.length != 32 || DEVELOPMENT_ENCRYPTION_KEY.equals(encryptionKey)
                || looksLikePlaceholder(encryptionKey)) {
            throw new IllegalStateException(
                    "APP_ENCRYPTION_KEY phải là khóa AES-256 Base64 riêng (32 byte)");
        }

        if (emailEnabled && (mailUsername == null || mailUsername.isBlank()
                || mailPassword == null || mailPassword.isBlank())) {
            throw new IllegalStateException(
                    "MAIL_USERNAME và MAIL_PASSWORD là bắt buộc khi MAIL_ENABLED=true");
        }
    }

    private boolean looksLikePlaceholder(String value) {
        String normalized = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("replace-with") || normalized.contains("change-this");
    }
}
