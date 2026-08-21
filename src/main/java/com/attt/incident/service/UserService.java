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
import com.attt.incident.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService securityAuditService;

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
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
        securityAuditService.log("USER_CREATED", "Tạo tài khoản: " + user.getUsername());
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = getUser(id);
        
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getDepartment() != null) {
            user.setDepartment(request.getDepartment());
        }
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }
        
        user = userRepository.save(user);
        securityAuditService.log("USER_UPDATED", "Cập nhật tài khoản: " + user.getUsername());
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
    public UserResponse assignRoles(Long id, Set<String> roleNames) {
        User user = getUser(id);
        user.setRoles(getRolesFromNames(roleNames));
        user = userRepository.save(user);
        securityAuditService.log("ROLE_CHANGED", "Thay đổi role của " + user.getUsername() + " thành " + roleNames);
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
        
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        securityAuditService.log("PASSWORD_CHANGED", "Đổi mật khẩu tài khoản: " + user.getUsername());
    }

    @Transactional
    public void adminResetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BadRequestException("Mật khẩu mới phải có ít nhất 6 ký tự");
        }
        
        User user = getUser(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        securityAuditService.log("PASSWORD_RESET", "Admin reset mật khẩu tài khoản: " + user.getUsername());
    }

    @Transactional
    public UserResponse lockUnlockAccount(Long id, boolean enabled) {
        User user = getUser(id);
        user.setEnabled(enabled);
        user = userRepository.save(user);
        securityAuditService.log(enabled ? "ACCOUNT_ENABLED" : "ACCOUNT_DISABLED", "Cập nhật trạng thái tài khoản: " + user.getUsername());
        return UserResponse.fromEntity(user);
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + id));
    }

    private Set<Role> getRolesFromNames(Set<String> roleNames) {
        Set<Role> roles = new HashSet<>();
        if (roleNames != null && !roleNames.isEmpty()) {
            for (String roleName : roleNames) {
                try {
                    RoleName enumRoleName = RoleName.valueOf(roleName.toUpperCase());
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
}
