package com.veritasvault.auth.config;

import com.veritasvault.auth.model.User;
import com.veritasvault.auth.model.enums.Role;
import com.veritasvault.auth.model.enums.UserStatus;
import com.veritasvault.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        String defaultHashedPassword = passwordEncoder.encode("Password123!");

        seedUserIfNotExists("admin@veritasvault.com", "System Administrator", Role.ROLE_ADMIN, defaultHashedPassword);
        seedUserIfNotExists("attorney@veritasvault.com", "Sarah Jenkins, Esq.", Role.ROLE_ATTORNEY, defaultHashedPassword);
        seedUserIfNotExists("examiner@veritasvault.com", "Marcus Vance, CCE", Role.ROLE_FORENSIC_EXAMINER, defaultHashedPassword);
        seedUserIfNotExists("client@veritasvault.com", "David Sterling", Role.ROLE_CLIENT, defaultHashedPassword);
    }

    private void seedUserIfNotExists(String email, String fullName, Role role, String passwordHash) {
        if (!userRepository.existsByEmail(email)) {
            User user = User.builder()
                    .fullName(fullName)
                    .email(email)
                    .passwordHash(passwordHash)
                    .role(role)
                    .status(UserStatus.ACTIVE)
                    .isEmailVerified(true)
                    .build();
            userRepository.save(user);
            log.info("Seeded default account: {} ({})", email, role);
        }
    }
}
