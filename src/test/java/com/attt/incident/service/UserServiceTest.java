package com.attt.incident.service;

import com.attt.incident.entity.User;
import com.attt.incident.repository.RefreshTokenRepository;
import com.attt.incident.repository.RoleRepository;
import com.attt.incident.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @InjectMocks UserService userService;

    @Test
    void unlockLoginAttemptsClearsTemporaryLock() {
        User user = User.builder().id(7L).username("analyst").email("a@example.com")
                .failedLoginAttempts(5).accountLockedUntil(LocalDateTime.now().plusMinutes(10)).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        var response = userService.unlockLoginAttempts(7L);

        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getAccountLockedUntil());
        assertFalse(response.isTemporarilyLocked());
    }

    @Test
    void revokeAllSessionsInvalidatesAccessAndRefreshTokens() {
        User user = User.builder().id(8L).username("manager").email("m@example.com")
                .tokenVersion(3L).build();
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));

        userService.revokeAllSessions(8L);

        assertEquals(4L, user.getTokenVersion());
        verify(refreshTokenRepository).deleteByUser(user);
        verify(userRepository).save(user);
    }
}
