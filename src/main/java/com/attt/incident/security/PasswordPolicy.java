package com.attt.incident.security;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Central password policy shared by bootstrap, recovery and user management. */
public final class PasswordPolicy {

    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "Admin@123", "Password@1", "P@ssword1", "P@ssw0rd",
            "Welcome@1", "Changeme@1", "Admin@1234"
    );

    private PasswordPolicy() {
    }

    public static String validate(String password) {
        if (password == null || password.length() < 12) {
            return "Mật khẩu phải có ít nhất 12 ký tự";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return "Mật khẩu không được vượt quá 72 byte";
        }
        if (!password.chars().anyMatch(Character::isUpperCase)) {
            return "Mật khẩu phải có ít nhất một chữ hoa";
        }
        if (!password.chars().anyMatch(Character::isLowerCase)) {
            return "Mật khẩu phải có ít nhất một chữ thường";
        }
        if (!password.chars().anyMatch(Character::isDigit)) {
            return "Mật khẩu phải có ít nhất một chữ số";
        }
        if (!password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
            return "Mật khẩu phải có ít nhất một ký tự đặc biệt";
        }
        if (WEAK_PASSWORDS.contains(password)) {
            return "Mật khẩu nằm trong danh sách mật khẩu yếu";
        }
        return null;
    }
}
