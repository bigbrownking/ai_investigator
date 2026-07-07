package org.di.digital.service.impl.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    public void sendResetPasswordEmail(String toEmail, String resetLink) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("Сброс пароля");
            message.setText(
                    "Для сброса пароля перейдите по ссылке:\n\n" + resetLink +
                            "\n\nСсылка действительна 1 час.\n" +
                            "Если вы не запрашивали сброс пароля — проигнорируйте это письмо."
            );
            mailSender.send(message);
            log.info("Reset password email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            throw new IllegalStateException("Не удалось отправить письмо");
        }
    }
}
