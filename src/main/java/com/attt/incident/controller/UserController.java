package com.attt.incident.controller;

import com.attt.incident.dto.ChangePasswordRequest;
import com.attt.incident.dto.UserCreateRequest;
import com.attt.incident.dto.UserResponse;
import com.attt.incident.dto.UserUpdateRequest;
import com.attt.incident.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ----- ADMIN ENDPOINTS -----

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasRole('ADMIN')")
    @PostMapping("/admin/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        UserResponse response = userService.createUser(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/admin/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/admin/users/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasRole('ADMIN')")
    @PutMapping("/admin/users/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasRole('ADMIN')")
    @PostMapping("/admin/users/{id}/roles")
    public ResponseEntity<UserResponse> assignRoles(@PathVariable Long id, @RequestBody Map<String, Set<String>> payload) {
        Set<String> roles = payload.get("roles");
        return ResponseEntity.ok(userService.assignRoles(id, roles));
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasRole('ADMIN')")
    @PostMapping("/admin/users/{id}/reset-password")
    public ResponseEntity<Map<String, String>> adminResetPassword(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String newPassword = payload.get("newPassword");
        userService.adminResetPassword(id, newPassword);
        return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công"));
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasRole('ADMIN')")
    @PostMapping("/admin/users/{id}/lock")
    public ResponseEntity<UserResponse> lockUnlockAccount(@PathVariable Long id, @RequestBody Map<String, Boolean> payload) {
        Boolean enabled = payload.get("enabled");
        if (enabled == null) enabled = false;
        return ResponseEntity.ok(userService.lockUnlockAccount(id, enabled));
    }

    // ----- USER ENDPOINTS -----

    @PostMapping("/users/change-password")
    public ResponseEntity<Map<String, String>> changePassword(Principal principal, @Valid @RequestBody ChangePasswordRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Chưa xác thực"));
        }
        userService.changePasswordByUsername(principal.getName(), request);
        return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công"));
    }
}
