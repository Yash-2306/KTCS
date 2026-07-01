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
            System.out.println("Initializing default users in the database...");
            LocalDate defaultJoin = LocalDate.of(2026, 1, 1);

            // 1. Create default Admin
            User admin = new User(
                    "admin",
                    passwordEncoder.encode("admin123"),
                    "ADMIN",
                    "Principal",
                    "9949789922"
            );
            admin.setJoiningDate(defaultJoin);
            userRepository.save(admin);

            // 2. Create 10 Teacher accounts using a loop
            for (int i = 1; i <= 10; i++) {
                User teacher = new User(
                        "teacher" + i,
                        passwordEncoder.encode("teacher123"),
                        "TEACHER",
                        "Teacher Number " + i,
                        "994978992" + i
                );
                teacher.setBaseSalaryPerDay(1000.0 + (i * 100)); // Incremental rates (1100, 1200, ...)
                teacher.setJoiningDate(defaultJoin.plusDays(i)); // Slightly staggered dates
                userRepository.save(teacher);
            }

            // 3. Create default Student 1 (Class 10 - Section A)
            User student1 = new User(
                    "student",
                    passwordEncoder.encode("student123"),
                    "STUDENT",
                    "John Connor",
                    "9949789924"
            );
            student1.setClassName("10");
            student1.setSection("A");
            student1.setJoiningDate(defaultJoin);
            userRepository.save(student1);

            // 4. Create Student 2 (Class 10 - Section A)
            User student2 = new User(
                    "student2",
                    passwordEncoder.encode("student123"),
                    "STUDENT",
                    "Thomas Edison",
                    "9949789926"
            );
            student2.setClassName("10");
            student2.setSection("A");
            student2.setJoiningDate(defaultJoin.plusDays(5));
            userRepository.save(student2);

            // 5. Create Student 3 (Class 10 - Section B)
            User student3 = new User(
                    "student3",
                    passwordEncoder.encode("student123"),
                    "STUDENT",
                    "Marie Curie",
                    "9949789927"
            );
            student3.setClassName("10");
            student3.setSection("B");
            student3.setJoiningDate(defaultJoin.plusDays(10));
            userRepository.save(student3);

            System.out.println("Default users successfully created!");
        }
    }
}
