package com.antsight.backend.config;

import com.antsight.backend.domain.user.User;
import com.antsight.backend.domain.user.UserRepository;
import com.antsight.backend.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private static final String ADMIN_EMAIL = "admin@antsight.com";
    private static final String ADMIN_PASSWORD = "Admin2026!";
    private static final String ADMIN_NICKNAME = "관리자";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            log.debug("Admin account already exists, skipping initialization.");
            return;
        }

        User admin = User.builder()
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .nickname(ADMIN_NICKNAME)
                .role(UserRole.ADMIN)
                .build();

        // 관리자는 즉시 승인 상태로 생성
        admin.approve(0L);

        userRepository.save(admin);
        log.info("Admin account created: {}", ADMIN_EMAIL);
    }
}
