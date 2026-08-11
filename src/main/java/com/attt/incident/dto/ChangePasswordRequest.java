package com.attt.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    
    // Nếu admin reset password thì không cần oldPassword, nhưng user tự đổi thì cần
    // Tùy theo logic nghiệp vụ, ta có thể cho null (nếu là Admin) hoặc validate trong service.
    @NotBlank(message = "Vui lòng nhập mật khẩu cũ")
    private String oldPassword;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 6, message = "Mật khẩu mới phải có ít nhất 6 ký tự")
    private String newPassword;
}
