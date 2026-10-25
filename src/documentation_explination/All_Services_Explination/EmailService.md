# EmailService Explanation

## What is EmailService?

In modern cloud systems and enterprise software, account security requires secure out-of-band communication with users. 

When a new user signs up or an existing user forgets their password, the server must be able to deliver one-time verification tokens to the user's private inbox.

`EmailService` is the outbound SMTP communications engine for VeritasVault. It works hand-in-hand with `AuthService` to deliver:
1. **Account Activation Links:** Cryptographically random 24-hour verification URLs.
2. **Password Reset Tokens:** Ephemeral 1-hour reset codes.

Crucially, it is architected with resilient error-handling so that temporary SMTP server outages or incorrect email addresses do not trigger unhandled exceptions that crash the user registration transaction.

---

## Variables Being Used and Their Reasons:

* **`mailSender`**: Spring's `JavaMailSender` interface. Handles low-level connection pooling, SSL/TLS negotiation, and SMTP socket communication (e.g. connecting to Gmail SMTP, Amazon SES, SendGrid, or local Mailhog).
* **`fromEmail`**: Injected via Spring's `@Value("${spring.mail.username:noreply@veritasvault.com}")`. Defines the authenticated sender identity displayed in the user's email client.
* **`baseUrl`**: Injected via Spring's `@Value("${app.server.base-url:http://localhost:8085}")`. Supplies the root domain and port so verification links are rendered as complete, clickable hyperlinks (e.g., `http://localhost:8085/api/auth/verify?token=...`).
* **`log`**: SLF4J logger instance (`LoggerFactory.getLogger(...)`). Records audit statements when emails are successfully dispatched or logs network failure traces for system debugging.

---

## Methods and Their Implementation Explained:

### 1. `sendVerificationEmail(String toEmail, String verificationToken)`: [Account Activation]
* **Link Assembly:**
  * Combines `baseUrl` with the verification endpoint and the unique token:
    ```
    http://localhost:8085/api/auth/verify?token=7b92f44a-d68b-4bfe-9ebc-214e6b12a0d1
    ```
* **Message Construction:**
  * Uses Spring's `SimpleMailMessage` to configure sender address (`fromEmail`), recipient address (`toEmail`), and an official subject line (`"VeritasVault - Verify Your Email Address"`).
  * Injects clear legal and instructions explaining that the link expires within 24 hours and can be ignored if the recipient did not initiate the request.
* **Safe Dispatch:**
  * Executes `mailSender.send(message)` inside a guarded `try-catch` block.
  * **Why the `try-catch`?** If the SMTP server is down or rejecting connections, throwing an uncaught exception would roll back the database transaction and prevent the user from being registered. By catching the error and logging `log.error(...)`, the account record remains safely saved in the database, allowing an administrator or resend task to assist the user.

### 2. `sendPasswordResetEmail(String toEmail, String resetToken)`: [Credential Recovery]
* **Message Construction:**
  * Builds a `SimpleMailMessage` addressed to the user.
  * Subject: `"VeritasVault - Password Reset Request"`.
  * Injects the ephemeral reset token with instructions that it expires in 1 hour.
  * Warns the user: `"If you did not request this, please notify your administrator immediately."`
* **Safe Dispatch:**
  * Calls `mailSender.send(message)`.
  * Catches potential transport exceptions and logs the outcome to SLF4J.

---

## Critical Design Questions & Summary:

| Question | Answer |
| :--- | :--- |
| **Why use `SimpleMailMessage` instead of HTML MimeMessage?** | `SimpleMailMessage` transmits clean, lightweight plain text. Plain text emails have the highest deliverability score across corporate firewalls and spam filters, avoiding aggressive junk-mail quarantine. |
| **Why catch `Exception` instead of letting it bubble up?** | Resiliency. In production systems, third-party mail relays (like SendGrid or AWS SES) can experience temporary timeouts. Swallowing the exception prevents a failed network hop from aborting database user registration. |
| **Where does the token get verified?** | The token delivered by this service is processed and validated by `AuthService.verifyEmail(token)` or `AuthService.resetPassword(...)`. |
