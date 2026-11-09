package com.veritasvault.auth.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@veritasvault.com}")
    private String fromEmail;

    @Value("${app.server.base-url:http://localhost:8080}")
    private String baseUrl;

    public void sendVerificationEmail(String toEmail, String verificationToken) {
        log.info("Sending verification email to {}", toEmail);
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Verification token for {}: {}", toEmail, verificationToken);
            return;
        }
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
            log.info("Verification email successfully sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        log.info("Sending password reset email to {}", toEmail);
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Reset token for {}: {}", toEmail, resetToken);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("VeritasVault - Password Reset Request");
            message.setText("A password reset request was received for your VeritasVault account.\n\n"
                    + "Your password reset token is:\n"
                    + resetToken + "\n\n"
                    + "This token will expire in 1 hour.\n\n"
                    + "If you did not request this, please contact your administrator immediately.");

            mailSender.send(message);
            log.info("Password reset email successfully sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }
}
