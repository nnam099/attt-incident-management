package com.attt.incident.security;

import com.attt.incident.entity.RefreshToken;
import com.attt.incident.repository.RefreshTokenRepository;
import com.attt.incident.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    @Value("${app.jwt.refresh-expiration-ms:604800000}") // Default 7 days
    private Long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Transactional
    public RefreshToken createRefreshToken(Long userId) {
        RefreshToken refreshToken = new RefreshToken();
        
        userRepository.findById(userId).ifPresent(user -> {
            refreshToken.setUser(user);
            refreshToken.setExpiryDate(LocalDateTime.now().plusNanos(refreshTokenDurationMs * 1000000));
            refreshToken.setToken(UUID.randomUUID().toString());
            
            // Xóa token cũ nếu có (1 user - 1 refresh token tại 1 thời điểm)
            refreshTokenRepository.deleteByUser(user);
            
            refreshTokenRepository.save(refreshToken);
        });

        return refreshToken;
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token đã hết hạn. Vui lòng đăng nhập lại.");
        }
        return token;
    }
}
