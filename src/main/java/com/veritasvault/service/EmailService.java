package com.veritasvault.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.server.base-url:http://localhost:8085}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(String toEmail, String verificationToken) {
        try {
            String verificationUrl = baseUrl + "/api/auth/verify?token=" + verificationToken;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("VeritasVault - Verify Your Email Address");
            message.setText("Welcome to VeritasVault.\n\n"
                    + "Please click the link below to verify your email address:\n"
                    + verificationUrl + "\n\n"
                    + "This link will expire in 24 hours.\n\n"
                    + "If you did not create an account, please ignore this email.");

            mailSender.send(message);
            log.info("Verification email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
            // Do not crash registration if email server has a temporary network blip
        }
    }
}