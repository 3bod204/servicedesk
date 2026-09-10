package com.tcc.servicedesk.config;

import com.tcc.servicedesk.user.User;
import com.tcc.servicedesk.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Profile("local")
public class DevDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmailIgnoreCase("agent@tcc.sa")) {
            return;
        }

        User user = User.builder()
                .email("agent@tcc.sa")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Test Agent")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        userRepository.save(user);
        System.out.println("Seeded test user: agent@tcc.sa / Password123!");
    }
}