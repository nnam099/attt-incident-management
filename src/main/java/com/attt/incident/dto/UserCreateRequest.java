package com.attt.incident.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class UserCreateRequest {

    @NotBlank(message = "Username không được để trống")
    @Size(min = 4, max = 100, message = "Username phải từ 4 đến 100 ký tự")
    private String username;

    @NotBlank(message = "Password không được để trống")
    @Size(min = 12, max = 72, message = "Mật khẩu phải từ 12 đến 72 ký tự")
    private String password;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 150, message = "Email không được vượt quá 150 ký tự")
    private String email;

    @Size(max = 150, message = "Họ tên không được vượt quá 150 ký tự")
    private String fullName;

    @Size(max = 100, message = "Phòng ban không được vượt quá 100 ký tự")
    private String department;

    @NotEmpty(message = "Vui lòng chọn ít nhất một vai trò")
    private Set<String> roles;
}
