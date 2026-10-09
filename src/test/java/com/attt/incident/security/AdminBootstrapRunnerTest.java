package com.attt.incident.security;

import com.attt.incident.entity.User;
import com.attt.incident.repository.RefreshTokenRepository;
import com.attt.incident.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminBootstrapRunnerTest {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private AdminBootstrapRunner runner;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        runner = new AdminBootstrapRunner(userRepository, refreshTokenRepository, passwordEncoder);
        ReflectionTestUtils.setField(runner, "adminUsername", "admin");
    }

    @Test
    void disabledBootstrapIsANoOp() {
        ReflectionTestUtils.setField(runner, "bootstrapEnabled", false);

        runner.run();

        verifyNoInteractions(userRepository, refreshTokenRepository, passwordEncoder);
    }

    @Test
    void enabledBootstrapFailsFastWhenPasswordIsMissing() {
        ReflectionTestUtils.setField(runner, "bootstrapEnabled", true);
        ReflectionTestUtils.setField(runner, "adminPassword", "");
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(User.builder().username("admin").enabled(false).build()));

        assertThatThrownBy(runner::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BOOTSTRAP_ADMIN_PASSWORD");
        verify(userRepository).findByUsername("admin");
        verifyNoInteractions(refreshTokenRepository, passwordEncoder);
    }

    @Test
    void enabledBootstrapFailsFastWhenPasswordIsWeak() {
        ReflectionTestUtils.setField(runner, "bootstrapEnabled", true);
        ReflectionTestUtils.setField(runner, "adminPassword", "Admin@123");
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(User.builder().username("admin").enabled(false).build()));

        assertThatThrownBy(runner::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("policy");
        verify(userRepository).findByUsername("admin");
        verifyNoInteractions(refreshTokenRepository, passwordEncoder);
    }

    @Test
    void enabledAdminSkipsBootstrapEvenWhenConfiguredPasswordIsStale() {
        ReflectionTestUtils.setField(runner, "bootstrapEnabled", true);
        ReflectionTestUtils.setField(runner, "adminPassword", "short");
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(User.builder().username("admin").enabled(true).build()));

        runner.run();

        verify(userRepository).findByUsername("admin");
        verifyNoInteractions(refreshTokenRepository, passwordEncoder);
    }
}
