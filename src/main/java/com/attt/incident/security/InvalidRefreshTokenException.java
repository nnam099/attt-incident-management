package com.attt.incident.security;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Refresh token không hợp lệ hoặc đã hết hạn");
    }
}
