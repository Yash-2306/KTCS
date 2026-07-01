package com.school.config;

import com.school.model.User;
import com.school.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Autowired
    public DatabaseInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }
    //PUBLIC STATIC VOID MAIN(String[]args)
    @Override
    public void run(String... args) throws Exception {
        // Only run if the database has no users
        if (userRepository.count() == 0) {
            System.out.println("Initializing default admin user in the database...");
            LocalDate defaultJoin = LocalDate.of(2026, 1, 1);

            // Read custom admin credentials from environment variables
            // Defaults to "admin" and "admin123" for local testing
            String adminUser = System.getenv("ADMIN_USERNAME");
            String adminPass = System.getenv("ADMIN_PASSWORD");

            if (adminUser == null || adminUser.isBlank()) {
                adminUser = "admin";
            }
            if (adminPass == null || adminPass.isBlank()) {
                adminPass = "admin123";
            }

            // Create admin
            User admin = new User(
                    adminUser,
                    passwordEncoder.encode(adminPass),
                    "ADMIN",
                    "Principal",
                    "9949789922"
            );
            admin.setJoiningDate(defaultJoin);
            userRepository.save(admin);

            System.out.println("Default admin user successfully created!");
        }
    }
}
