package com.attt.incident.service;

import com.attt.incident.dto.ChangePasswordRequest;
import com.attt.incident.dto.UserCreateRequest;
import com.attt.incident.dto.UserResponse;
import com.attt.incident.dto.UserUpdateRequest;
import com.attt.incident.entity.Role;
import com.attt.incident.entity.RoleName;
import com.attt.incident.entity.User;
import com.attt.incident.exception.BadRequestException;
import com.attt.incident.exception.ResourceNotFoundException;
import com.attt.incident.repository.RoleRepository;
import com.attt.incident.repository.RefreshTokenRepository;
import com.attt.incident.repository.UserRepository;
import com.attt.incident.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        validatePassword(request.getPassword());
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username đã tồn tại");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email đã tồn tại");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .fullName(request.getFullName())
                .department(request.getDepartment())
                .enabled(true)
                .roles(getRolesFromNames(request.getRoles()))
                .build();

        user = userRepository.save(user);
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request, String currentUsername) {
        User user = getUser(id);

        preventSelfLock(user, request.getEnabled(), currentUsername);
        preventSelfAdminRemoval(user, request.getRoles(), currentUsername);
        
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email đã tồn tại");
            }
            user.setEmail(request.getEmail());
        }
        if (request.getDepartment() != null) {
            user.setDepartment(request.getDepartment());
        }
        if (request.getEnabled() != null) {
            if (!request.getEnabled() && user.isEnabled()) {
                user.setTokenVersion(user.getTokenVersion() + 1);
                refreshTokenRepository.deleteByUser(user);
            }
            user.setEnabled(request.getEnabled());
        }
        if (request.getRoles() != null) {
            if (request.getRoles().isEmpty()) {
                throw new BadRequestException("Người dùng phải có ít nhất một vai trò");
            }
            user.setRoles(getRolesFromNames(request.getRoles()));
        }
        
        user = userRepository.save(user);
        return UserResponse.fromEntity(user);
    }

    public UserResponse getUserById(Long id) {
        return UserResponse.fromEntity(getUser(id));
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse assignRoles(Long id, Set<String> roleNames, String currentUsername) {
        User user = getUser(id);
        if (roleNames == null || roleNames.isEmpty()) {
            throw new BadRequestException("Người dùng phải có ít nhất một vai trò");
        }
        preventSelfAdminRemoval(user, roleNames, currentUsername);
        user.setRoles(getRolesFromNames(roleNames));
        user = userRepository.save(user);
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public void changePassword(Long id, ChangePasswordRequest request) {
        User user = getUser(id);
        performPasswordChange(user, request);
    }
    
    @Transactional
    public void changePasswordByUsername(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
        performPasswordChange(user, request);
    }

    private void performPasswordChange(User user, ChangePasswordRequest request) {
        if (request.getOldPassword() == null || request.getOldPassword().isBlank()) {
            throw new BadRequestException("Vui lòng nhập mật khẩu cũ");
        }
        
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("Mật khẩu cũ không chính xác");
        }
        
        validatePassword(request.getNewPassword());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setPasswordChangedAt(LocalDateTime.now());
        refreshTokenRepository.deleteByUser(user);
        userRepository.save(user);
    }

    @Transactional
    public void adminResetPassword(Long id, String newPassword) {
        validatePassword(newPassword);
        
        User user = getUser(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setPasswordChangedAt(LocalDateTime.now());
        refreshTokenRepository.deleteByUser(user);
        userRepository.save(user);
    }

    @Transactional
    public UserResponse lockUnlockAccount(Long id, boolean enabled, String currentUsername) {
        User user = getUser(id);
        preventSelfLock(user, enabled, currentUsername);
        if (!enabled && user.isEnabled()) {
            user.setTokenVersion(user.getTokenVersion() + 1);
            refreshTokenRepository.deleteByUser(user);
        }
        user.setEnabled(enabled);
        user = userRepository.save(user);
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse unlockLoginAttempts(Long id) {
        User user = getUser(id);
        user.setFailedLoginAttempts(0);
        user.setAccountLockedUntil(null);
        return UserResponse.fromEntity(userRepository.save(user));
    }

    @Transactional
    public void revokeAllSessions(Long id) {
        User user = getUser(id);
        user.setTokenVersion(user.getTokenVersion() + 1);
        refreshTokenRepository.deleteByUser(user);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<com.attt.incident.dto.AssigneeResponse> getAssignableUsers() {
        return userRepository.findByRoleName(RoleName.ANALYST).stream()
                .map(user -> new com.attt.incident.dto.AssigneeResponse(
                        user.getId(), user.getUsername(), user.getFullName()))
                .toList();
    }

    @Transactional
    public void deleteUser(Long id, String currentUsername) {
        User user = getUser(id);
        if (currentUsername != null && user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new BadRequestException("Không thể tự xóa tài khoản quản trị của chính mình");
        }
        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BadRequestException("Không thể xóa người dùng này do có dữ liệu sự cố hoặc nhật ký liên quan. Bạn có thể chọn 'Khóa tài khoản' để vô hiệu hóa.");
        }
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + id));
    }

    private void preventSelfLock(User user, Boolean enabled, String currentUsername) {
        if (Boolean.FALSE.equals(enabled)
                && user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new BadRequestException("Không thể tự khóa tài khoản của chính mình");
        }
    }

    private void preventSelfAdminRemoval(User user, Set<String> roleNames, String currentUsername) {
        if (roleNames == null || !user.getUsername().equalsIgnoreCase(currentUsername)) {
            return;
        }
        boolean keepsAdmin = roleNames.stream()
                .filter(java.util.Objects::nonNull)
                .map(role -> role.trim().toUpperCase())
                .anyMatch(role -> role.equals("ADMIN") || role.equals("ROLE_ADMIN"));
        if (!keepsAdmin) {
            throw new BadRequestException("Không thể tự gỡ vai trò ADMIN của chính mình");
        }
    }

    private Set<Role> getRolesFromNames(Set<String> roleNames) {
        Set<Role> roles = new HashSet<>();
        if (roleNames != null && !roleNames.isEmpty()) {
            for (String roleName : roleNames) {
                try {
                    String cleanName = roleName != null ? roleName.trim().toUpperCase() : "";
                    if (cleanName.startsWith("ROLE_")) {
                        cleanName = cleanName.substring(5);
                    }
                    RoleName enumRoleName = RoleName.valueOf(cleanName);
                    Role role = roleRepository.findByName(enumRoleName)
                            .orElseThrow(() -> new BadRequestException("Không tìm thấy role: " + roleName));
                    roles.add(role);
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Role không hợp lệ: " + roleName);
                }
            }
        }
        return roles;
    }

    private void validatePassword(String password) {
        String error = PasswordPolicy.validate(password);
        if (error != null) throw new BadRequestException(error);
    }
}
