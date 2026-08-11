package com.attt.incident.controller;

import com.attt.incident.dto.AuthResponse;
import com.attt.incident.dto.LoginRequest;
import com.attt.incident.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.attt.incident.dto.RefreshTokenRequest;
import com.attt.incident.entity.RefreshToken;
import com.attt.incident.entity.User;
import com.attt.incident.exception.BadRequestException;
import com.attt.incident.repository.UserRepository;
import com.attt.incident.security.LoginAttemptService;
import com.attt.incident.security.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.AuthenticationException;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final com.attt.incident.security.AppUserDetailsService userDetailsService;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();

        if (loginAttemptService.isAccountLocked(request.getUsername())) {
            throw new BadRequestException("Tài khoản của bạn đã bị khóa tạm thời do nhập sai quá nhiều lần. Vui lòng thử lại sau.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (AuthenticationException e) {
            loginAttemptService.loginFailed(request.getUsername(), ipAddress);
            throw new org.springframework.security.authentication.BadCredentialsException("Sai tên đăng nhập hoặc mật khẩu");
        }

        // Login successful
        loginAttemptService.loginSucceeded(request.getUsername(), ipAddress);

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
        String token = jwtService.generateToken(userDetails);
        
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadRequestException("Không tìm thấy user"));
                
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        List<String> roles = userDetails.getAuthorities().stream()
                .map(Object::toString)
                .toList();

        return ResponseEntity.ok(new AuthResponse(token, refreshToken.getToken(), userDetails.getUsername(), roles));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return refreshTokenService.findByToken(request.getRefreshToken())
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
                    String token = jwtService.generateToken(userDetails);
                    List<String> roles = userDetails.getAuthorities().stream()
                            .map(Object::toString)
                            .toList();
                    return ResponseEntity.ok(new AuthResponse(token, request.getRefreshToken(), user.getUsername(), roles));
                })
                .orElseThrow(() -> new BadRequestException("Refresh token không tồn tại trong hệ thống."));
    }
}
