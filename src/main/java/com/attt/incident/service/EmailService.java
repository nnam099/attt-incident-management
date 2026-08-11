package com.attt.incident.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Async
    public void sendEmail(String to, String subject, String text) {
        if (to == null || to.isBlank()) {
            log.warn("Bỏ qua gửi email vì địa chỉ người nhận trống (Subject: {})", subject);
            return;
        }
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            
            mailSender.send(message);
            log.info("Đã gửi email thành công tới {}", to);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email tới {}: {}", to, e.getMessage());
        }
    }
}
