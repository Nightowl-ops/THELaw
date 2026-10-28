package com.veritasvault.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@veritasvault.com}")
    private String fromEmail;

    @Value("${app.server.base-url:http://localhost:8085}")
    private String baseUrl;

    @Async("emailTaskExecutor")
    public void sendVerificationEmail(String toEmail, String verificationToken) {
        log.info("[Async-Thread: {}] Preparing to send verification email to {}",
                Thread.currentThread().getName(), toEmail);
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
            log.info("[Async-Thread: {}] Verification email successfully sent to {}",
                    Thread.currentThread().getName(), toEmail);
        } catch (Exception e) {
            log.error("[Async-Thread: {}] Failed to send verification email to {}: {}",
                    Thread.currentThread().getName(), toEmail, e.getMessage());
        }
    }

    @Async("emailTaskExecutor")
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        log.info("[Async-Thread: {}] Preparing to send password reset email to {}",
                Thread.currentThread().getName(), toEmail);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("VeritasVault - Password Reset Request");
            message.setText("Hello,\n\n"
                    + "A password reset request was initiated for your VeritasVault account.\n\n"
                    + "Your password reset token is:\n" + resetToken + "\n\n"
                    + "This token will expire in 1 hour.\n\n"
                    + "If you did not request this, please notify your administrator immediately.");

            mailSender.send(message);
            log.info("[Async-Thread: {}] Password reset email successfully sent to {}",
                    Thread.currentThread().getName(), toEmail);
        } catch (Exception e) {
            log.error("[Async-Thread: {}] Failed to send password reset email to {}: {}",
                    Thread.currentThread().getName(), toEmail, e.getMessage());
        }
    }
}