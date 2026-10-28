package com.veritasvault.service;

import com.veritasvault.config.AsyncConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {AsyncConfig.class, EmailService.class})
class EmailServiceTest {

    @Autowired
    private EmailService emailService;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    @DisplayName("sendVerificationEmail executes asynchronously on email-worker thread")
    void sendVerificationEmail_ExecutesOnDedicatedAsyncThread() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> executingThreadName = new AtomicReference<>();

        doAnswer(invocation -> {
            executingThreadName.set(Thread.currentThread().getName());
            latch.countDown();
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        String callerThreadName = Thread.currentThread().getName();
        emailService.sendVerificationEmail("attorney@lawfirm.com", "token-xyz-123");

        boolean completed = latch.await(3, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(executingThreadName.get())
                .isNotNull()
                .startsWith("email-worker-")
                .isNotEqualTo(callerThreadName);
    }

    @Test
    @DisplayName("sendPasswordResetEmail executes asynchronously on email-worker thread")
    void sendPasswordResetEmail_ExecutesOnDedicatedAsyncThread() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> executingThreadName = new AtomicReference<>();

        doAnswer(invocation -> {
            executingThreadName.set(Thread.currentThread().getName());
            latch.countDown();
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        String callerThreadName = Thread.currentThread().getName();
        emailService.sendPasswordResetEmail("client@client.com", "reset-token-456");

        boolean completed = latch.await(3, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(executingThreadName.get())
                .isNotNull()
                .startsWith("email-worker-")
                .isNotEqualTo(callerThreadName);
    }
}
