package com.attt.incident.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.notifications.email-enabled:false}")
    private boolean emailEnabled;

    public boolean isEnabled() {
        return emailEnabled;
    }

    @Async
    public CompletableFuture<Boolean> sendEmail(String to, String subject, String text) {
        if (!emailEnabled) {
            log.debug("Bỏ qua email vì thông báo SMTP chưa được bật");
            return CompletableFuture.completedFuture(false);
        }
        if (to == null || to.isBlank()) {
            log.warn("Bỏ qua gửi email vì địa chỉ người nhận trống (Subject: {})", subject);
            return CompletableFuture.completedFuture(false);
        }
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            
            mailSender.send(message);
            log.info("Đã gửi email thành công tới {}", to);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email tới {}: {}", to, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }
}
