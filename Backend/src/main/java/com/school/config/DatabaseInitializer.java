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
        // Automatically delete the default admin account from the database if present
        userRepository.findByUsername("admin").ifPresent(u -> {
            userRepository.delete(u);
            System.out.println("Default insecure 'admin' account deleted successfully!");
        });
        System.out.println("Database Initializer: Startup checks complete.");
    }
}
