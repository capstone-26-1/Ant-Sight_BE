package com.antsight.backend.config;

import com.antsight.backend.domain.user.User;
import com.antsight.backend.domain.user.UserRepository;
import com.antsight.backend.domain.user.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminInitializer implements CommandLineRunner {

    private static final String ADMIN_EMAIL = "admin@antsight.com";
    private static final String ADMIN_NICKNAME = "관리자";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public AdminInitializer(UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            @Value("${admin.initial-password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            log.debug("Admin account already exists, skipping initialization.");
            return;
        }

        if (adminPassword == null || adminPassword.length() < 8) {
            log.warn("ADMIN_INITIAL_PASSWORD 미설정(또는 8자 미만) — 관리자 자동 생성 스킵. ENV로 주입하세요.");
            return;
        }

        User admin = User.builder()
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .nickname(ADMIN_NICKNAME)
                .role(UserRole.ADMIN)
                .build();

        admin.approve(0L);

        userRepository.save(admin);
        log.info("Admin account created: {}", ADMIN_EMAIL);
    }
}
