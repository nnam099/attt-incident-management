package com.attt.incident.security;

import com.attt.incident.entity.RefreshToken;
import com.attt.incident.repository.RefreshTokenRepository;
import com.attt.incident.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    @Value("${app.jwt.refresh-expiration-ms:604800000}") // Default 7 days
    private Long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public String createRefreshToken(Long userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(InvalidRefreshTokenException::new);
        String rawToken = UUID.randomUUID().toString() + UUID.randomUUID();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(expiryFromNow());
        refreshToken.setToken(hash(rawToken));
        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.flush();
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    /** Consumes the old token and atomically rotates it to prevent replay. */
    @Transactional
    public RotatedRefreshToken rotateRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        RefreshToken token = refreshTokenRepository.findByTokenForUpdate(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!token.getExpiryDate().isAfter(LocalDateTime.now()) || !token.getUser().isEnabled()) {
            refreshTokenRepository.delete(token);
            throw new InvalidRefreshTokenException();
        }

        String replacement = UUID.randomUUID().toString() + UUID.randomUUID();
        token.setToken(hash(replacement));
        token.setExpiryDate(expiryFromNow());
        refreshTokenRepository.save(token);
        return new RotatedRefreshToken(token.getUser(), replacement);
    }

    @Transactional
    public void revokeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokenRepository.findByToken(hash(rawToken)).ifPresent(refreshTokenRepository::delete);
    }

    private LocalDateTime expiryFromNow() {
        return LocalDateTime.now().plusNanos(Math.multiplyExact(refreshTokenDurationMs, 1_000_000L));
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 không khả dụng", e);
        }
    }

    public record RotatedRefreshToken(com.attt.incident.entity.User user, String token) {}
}
