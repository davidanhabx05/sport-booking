package vn.sportbooking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Gửi email bất đồng bộ (@Async) để request đặt sân không phải chờ SMTP.
 * Khi chưa cấu hình (app.mail.enabled=false) chỉ ghi log nội dung email – tiện khi demo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.mail.from:SanNay <no-reply@sannay.vn>}")
    private String from;

    @Async
    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) return;
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!enabled || sender == null) {
            log.info("[MAIL – chưa bật gửi thật] To: {} | Subject: {}\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
            log.info("Sent mail to {}: {}", to, subject);
        } catch (Exception e) {
            // Lỗi email không được làm hỏng nghiệp vụ đặt sân
            log.warn("Gửi email tới {} thất bại: {}", to, e.getMessage());
        }
    }
}
