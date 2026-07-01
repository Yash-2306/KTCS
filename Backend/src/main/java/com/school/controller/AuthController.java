package com.school.controller;

import com.school.model.User;
import com.school.model.LeftUser;
import com.school.repository.UserRepository;
import com.school.repository.LeftUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.school.config.JwtUtil;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final LeftUserRepository leftUserRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Autowired
    public AuthController(UserRepository userRepository,
                          LeftUserRepository leftUserRepository,
                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.leftUserRepository = leftUserRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtUtil = jwtUtil;
    }

    // ── 1. LOGIN ──────────────────────────────────────────────────────────────
    //
    // What happens here (explain in interviews):
    //   1. User sends username + password + role
    //   2. We find the user in the database by username
    //   3. BCrypt checks if the submitted password matches the stored hash
    //   4. If yes AND the role matches → generate a JWT token and return it
    //   5. Frontend stores the token; sends it in every future request header
    //
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String password = loginData.get("password");
        String role     = loginData.get("role");

        Map<String, String> resp = new HashMap<>();

        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            boolean passwordOk = passwordEncoder.matches(password, user.getPassword());
            boolean roleOk     = user.getRole().equalsIgnoreCase(role);

            if (passwordOk && roleOk) {
                // Generate a JWT token valid for 8 hours
                String token = jwtUtil.generateToken(
                        user.getId(),
                        user.getUsername(),
                        user.getRole().toUpperCase()
                );

                resp.put("status",   "SUCCESS");
                resp.put("token",    token);                          // ← send token to frontend
                resp.put("userId",   String.valueOf(user.getId()));
                resp.put("username", user.getUsername());
                resp.put("role",     user.getRole().toUpperCase());
                resp.put("fullName", user.getFullName() != null ? user.getFullName() : username);
                return ResponseEntity.ok(resp);
            }
        }

        resp.put("status",  "ERROR");
        resp.put("message", "Invalid username, password, or role choice.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
    }

    // 2. REGISTER/CREATE USER ENDPOINT (Useful for manual testing)
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        // Check if username already exists
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already exists!");
        }

        // Hash the password before saving to database
        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPassword);

        // Normalize role to uppercase (ADMIN, TEACHER, STUDENT)
        user.setRole(user.getRole().toUpperCase());

        // Default joining date to today if null
        if (user.getJoiningDate() == null) {
            user.setJoiningDate(LocalDate.now());
        }

        User savedUser = userRepository.save(user);
        return ResponseEntity.ok(savedUser);
    }

    // 3. UPDATE USER ENDPOINT (Admin can edit any user profile)
    @PutMapping("/users/{id}/update")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> updateData) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found!");
        }

        User user = userOpt.get();
        if (updateData.containsKey("fullName")) user.setFullName(updateData.get("fullName").toString());
        if (updateData.containsKey("phone")) user.setPhone(updateData.get("phone").toString());
        
        if (updateData.containsKey("className")) user.setClassName(updateData.get("className").toString());
        if (updateData.containsKey("section")) user.setSection(updateData.get("section").toString());

        if (updateData.containsKey("baseSalaryPerDay")) {
            user.setBaseSalaryPerDay(Double.valueOf(updateData.get("baseSalaryPerDay").toString()));
        }

        if (updateData.containsKey("joiningDate") && updateData.get("joiningDate") != null && !updateData.get("joiningDate").toString().isEmpty()) {
            user.setJoiningDate(LocalDate.parse(updateData.get("joiningDate").toString()));
        }

        User saved = userRepository.save(user);
        return ResponseEntity.ok(saved);
    }

    // 4. MARK USER AS LEFT (Archive user details, delete active user account)
    @PostMapping("/users/{id}/leave")
    public ResponseEntity<?> markUserAsLeft(@PathVariable Long id, @RequestParam String leavingDate) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Active user profile not found!");
        }

        User user = userOpt.get();
        LocalDate lDate = LocalDate.parse(leavingDate);

        // Copy and save details into LeftUser
        LeftUser leftUser = new LeftUser(user, lDate);
        leftUserRepository.save(leftUser);

        // Delete active User account so they can no longer log in
        userRepository.delete(user);

        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "message", "User successfully archived as left. Account deactivated."
        ));
    }

    // 5. GET ALL LEFT/ARCHIVED USERS
    @GetMapping("/users/left")
    public ResponseEntity<List<LeftUser>> getLeftUsers() {
        List<LeftUser> leftList = leftUserRepository.findAll();
        return ResponseEntity.ok(leftList);
    }

    // 6. GET ALL ACTIVE STUDENTS (used by Admin Roster panel)
    @GetMapping("/users/active/students")
    public ResponseEntity<List<User>> getActiveStudents() {
        List<User> students = userRepository.findByRole("STUDENT");
        return ResponseEntity.ok(students);
    }

    // 7. GET ALL ACTIVE TEACHERS (used by Admin Roster panel)
    @GetMapping("/users/active/teachers")
    public ResponseEntity<List<User>> getActiveTeachers() {
        List<User> teachers = userRepository.findByRole("TEACHER");
        return ResponseEntity.ok(teachers);
    }

    // 8. RESET PASSWORD (Admin only — no old password needed)
    //    The admin sends their own userId so the backend can verify they are actually an ADMIN.
    //    This is a simple approach — no JWT required.
    @PutMapping("/users/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id,
                                           @RequestBody Map<String, Object> data) {
        // Step 1: Check that the requester is an admin
        Object adminIdObj = data.get("adminUserId");
        if (adminIdObj == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Admin ID is required to reset passwords.");
        }

        Long adminId = Long.valueOf(adminIdObj.toString());
        Optional<User> adminOpt = userRepository.findById(adminId);

        if (adminOpt.isEmpty() || !adminOpt.get().getRole().equalsIgnoreCase("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Only admins can reset passwords.");
        }

        // Step 2: Find the target user (teacher or student)
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found!");
        }

        // Step 3: Validate the new password
        Object newPassObj = data.get("newPassword");
        if (newPassObj == null || newPassObj.toString().trim().length() < 4) {
            return ResponseEntity.badRequest().body("Password must be at least 4 characters.");
        }

        // Step 4: Hash and save the new password
        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode(newPassObj.toString().trim()));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Password for " + user.getFullName() + " has been reset successfully."
        ));
    }
}

