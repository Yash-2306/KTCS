package com.school.config;

import com.school.model.User;
import com.school.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

/**
 * DatabaseInitializer — runs once on every startup.
 *
 * 1. Deletes the old default 'admin' account if it still exists (security).
 * 2. Creates a new admin account from environment variables ADMIN_USERNAME and
 *    ADMIN_PASSWORD on first startup — only if no admin account exists yet.
 */
@Component
public class DatabaseInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Autowired
    public DatabaseInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. Remove the old insecure default admin if present
        userRepository.findByUsername("admin").ifPresent(u -> {
            userRepository.delete(u);
            System.out.println("[Startup] Default insecure 'admin' account removed.");
        });

        // 2. Create admin from environment variables if no admin exists yet
        boolean adminExists = !userRepository.findByRole("ADMIN").isEmpty();
        if (!adminExists) {
            String adminUsername = System.getenv("ADMIN_USERNAME");
            String adminPassword = System.getenv("ADMIN_PASSWORD");

            if (adminUsername != null && !adminUsername.isBlank()
                    && adminPassword != null && !adminPassword.isBlank()) {

                User admin = new User();
                admin.setUsername(adminUsername.trim());
                admin.setPassword(passwordEncoder.encode(adminPassword.trim()));
                admin.setRole("ADMIN");
                admin.setFullName("Administrator");
                admin.setJoiningDate(LocalDate.now());
                userRepository.save(admin);
                System.out.println("[Startup] Admin account created: " + adminUsername);
            } else {
                System.out.println("[Startup] NOTICE: No admin account exists. Set ADMIN_USERNAME and ADMIN_PASSWORD environment variables to automatically initialize the admin account.");
            }
        } else {
            System.out.println("[Startup] Admin account exists. Startup check complete.");
        }
    }
}

