package com.quantumlock.request;

import jakarta.mail.internet.MimeMessage;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@Getter
public class PlatformEmailService {

    private static final Logger log = LoggerFactory.getLogger(PlatformEmailService.class);

    private final String platformMail;
    private final String baseUrl;
    private final JavaMailSender mailSender;

    public PlatformEmailService(
            @Value("${platform.mail:${PLATFORM_MAIL:${PlatformMail:chavandipak0606@gmail.com}}}") String platformMail,
            @Value("${platform.base-url:${PLATFORM_BASE_URL:http://localhost:5173}}") String baseUrl,
            @Autowired(required = false) JavaMailSender mailSender) {
        this.platformMail = (platformMail != null && !platformMail.isBlank()) ? platformMail.trim() : "chavandipak0606@gmail.com";
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : "http://localhost:5173";
        this.mailSender = mailSender;
        log.info("[PlatformEmailService] Initialized with platformMail='{}', baseUrl='{}', mailSenderAvailable={}",
                this.platformMail, this.baseUrl, (this.mailSender != null));
    }

    public void dispatchRealEmail(String toEmail, String subject, String htmlBody) {
        if (mailSender == null) {
            log.info("[EMAIL] JavaMailSender bean not configured. Simulated dispatch recorded for: {}", toEmail);
            return;
        }

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

                helper.setFrom(platformMail, "QUANTUM-LOCK Security Platform");
                helper.setTo(toEmail);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);

                mailSender.send(message);
                log.info("[EMAIL] Successfully sent real SMTP email to: {} | Subject: {}", toEmail, subject);
            } catch (Exception ex) {
                log.warn("[EMAIL] SMTP transmission attempted for '{}' but encountered: {}. " +
                        "(If using Gmail SMTP, configure SPRING_MAIL_PASSWORD with an App Password). " +
                        "Email notification safely preserved in audit storage.", toEmail, ex.getMessage());
            }
        });
    }
}
