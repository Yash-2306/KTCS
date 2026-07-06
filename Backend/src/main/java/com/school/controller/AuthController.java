package com.school.controller;

import com.school.model.AuditLog;
import com.school.model.LeftUser;
import com.school.model.User;
import com.school.repository.AuditLogRepository;
import com.school.repository.LeftUserRepository;
import com.school.repository.UserRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import com.school.config.JwtUtil;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final LeftUserRepository leftUserRepository;
    private final AuditLogRepository auditLogRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    @Autowired
    public AuthController(UserRepository userRepository,
                          LeftUserRepository leftUserRepository,
                          AuditLogRepository auditLogRepository,
                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.leftUserRepository = leftUserRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtUtil = jwtUtil;
    }

    // ── Helper: save audit log ────────────────────────────────────────
    private void audit(String action, String performedBy, String targetUser, String details) {
        try {
            auditLogRepository.save(new AuditLog(action, performedBy, targetUser, details));
        } catch (Exception ignored) { /* never let audit failure break the main flow */ }
    }

    // ── 1. LOGIN ──────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String password = loginData.get("password");
        String role     = loginData.get("role");

        Map<String, String> resp = new HashMap<>();
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isEmpty()) {
            resp.put("status", "ERROR");
            resp.put("message", "Invalid username, password, or role choice.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
        }

        User user = userOpt.get();

        // ── Account lockout check ──────────────────────────────────────
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long minutesLeft = java.time.Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes() + 1;
            resp.put("status", "LOCKED");
            resp.put("message", "Account locked due to too many failed attempts. Try again in " + minutesLeft + " minute(s).");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(resp);
        }

        // ── Password + role check ──────────────────────────────────────
        boolean passwordOk = passwordEncoder.matches(password, user.getPassword());
        boolean roleOk     = user.getRole().equalsIgnoreCase(role);

        if (passwordOk && roleOk) {
            // Reset lockout state on success
            user.setLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);

            String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().toUpperCase());

            audit("LOGIN", user.getUsername(), null, "Successful login from role: " + role.toUpperCase());

            resp.put("status",   "SUCCESS");
            resp.put("token",    token);
            resp.put("userId",   String.valueOf(user.getId()));
            resp.put("username", user.getUsername());
            resp.put("role",     user.getRole().toUpperCase());
            resp.put("fullName", user.getFullName() != null ? user.getFullName() : username);
            return ResponseEntity.ok(resp);
        }

        // ── Failed login — increment attempt counter ───────────────────
        int attempts = user.getLoginAttempts() + 1;
        user.setLoginAttempts(attempts);

        if (attempts >= MAX_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
            userRepository.save(user);
            audit("ACCOUNT_LOCKED", username, username,
                    "Account locked after " + MAX_ATTEMPTS + " failed login attempts.");
            resp.put("status", "LOCKED");
            resp.put("message", "Too many failed attempts. Account locked for " + LOCKOUT_MINUTES + " minutes.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(resp);
        }

        userRepository.save(user);
        resp.put("status",  "ERROR");
        resp.put("message", "Invalid username, password, or role choice. (" + (MAX_ATTEMPTS - attempts) + " attempts remaining)");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
    }

    // ── 2. REGISTER (create single user) ─────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already exists!");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole(user.getRole().toUpperCase());
        if (user.getJoiningDate() == null) user.setJoiningDate(LocalDate.now());

        User saved = userRepository.save(user);
        audit("USER_CREATED", "system", user.getUsername(),
                "Created " + user.getRole() + " account: " + user.getUsername());
        return ResponseEntity.ok(saved);
    }

    // ── 3. BULK CSV IMPORT ────────────────────────────────────────────
    /**
     * Upload a CSV file to create multiple users at once.
     * CSV columns: full_name, username, password, role, class_name, section, phone
     *
     * Example row:
     *   Rahul Sharma, rahul123, Welcome@1, STUDENT, 10, A, 9876543210
     *
     * Returns a summary: how many created, which ones failed and why.
     * Interview: "We used Apache Commons CSV to parse uploaded files server-side.
     * Each row is validated before being saved. Failed rows are returned to the
     * admin with the specific error so they can fix and re-upload."
     */
    @PostMapping("/users/bulk-import")
    public ResponseEntity<?> bulkImport(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("CSV file is empty.");

        List<Map<String, String>> created = new ArrayList<>();
        List<Map<String, String>> failed  = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim())) {

            for (CSVRecord record : csvParser) {
                String username = "";
                try {
                    username    = record.get("username").trim();
                    String fn   = record.get("full_name").trim();
                    String pass = record.get("password").trim();
                    String role = record.get("role").trim().toUpperCase();
                    String cls  = getOptional(record, "class_name");
                    String sec  = getOptional(record, "section");
                    String ph   = getOptional(record, "phone");

                    if (username.isEmpty() || pass.isEmpty() || role.isEmpty()) {
                        Map<String, String> err = new HashMap<>();
                        err.put("row", String.valueOf(csvParser.getCurrentLineNumber()));
                        err.put("username", username);
                        err.put("error", "username, password, and role are required.");
                        failed.add(err);
                        continue;
                    }

                    if (userRepository.findByUsername(username).isPresent()) {
                        Map<String, String> err = new HashMap<>();
                        err.put("row", String.valueOf(csvParser.getCurrentLineNumber()));
                        err.put("username", username);
                        err.put("error", "Username already exists.");
                        failed.add(err);
                        continue;
                    }

                    User u = new User();
                    u.setUsername(username);
                    u.setFullName(fn);
                    u.setPassword(passwordEncoder.encode(pass));
                    u.setRole(role);
                    u.setClassName(cls);
                    u.setSection(sec);
                    u.setPhone(ph);
                    u.setJoiningDate(LocalDate.now());
                    userRepository.save(u);

                    Map<String, String> ok = new HashMap<>();
                    ok.put("username", username);
                    ok.put("role", role);
                    created.add(ok);

                } catch (Exception e) {
                    Map<String, String> err = new HashMap<>();
                    err.put("username", username);
                    err.put("error", e.getMessage());
                    failed.add(err);
                }
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to parse CSV: " + e.getMessage());
        }

        audit("BULK_IMPORT", "admin", null,
                "Bulk import: " + created.size() + " created, " + failed.size() + " failed.");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCreated", created.size());
        summary.put("totalFailed", failed.size());
        summary.put("created", created);
        summary.put("failed", failed);
        return ResponseEntity.ok(summary);
    }

    private String getOptional(CSVRecord record, String key) {
        try { return record.get(key).trim(); } catch (Exception e) { return null; }
    }

    // ── 4. UPDATE USER ────────────────────────────────────────────────
    @PutMapping("/users/{id}/update")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> updateData) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.badRequest().body("User not found!");

        User user = userOpt.get();
        if (updateData.containsKey("fullName")) user.setFullName(updateData.get("fullName").toString());
        if (updateData.containsKey("phone")) user.setPhone(updateData.get("phone").toString());
        if (updateData.containsKey("className")) user.setClassName(updateData.get("className").toString());
        if (updateData.containsKey("section")) user.setSection(updateData.get("section").toString());
        if (updateData.containsKey("baseSalaryPerDay"))
            user.setBaseSalaryPerDay(Double.valueOf(updateData.get("baseSalaryPerDay").toString()));
        if (updateData.containsKey("joiningDate") && updateData.get("joiningDate") != null
                && !updateData.get("joiningDate").toString().isEmpty())
            user.setJoiningDate(LocalDate.parse(updateData.get("joiningDate").toString()));

        return ResponseEntity.ok(userRepository.save(user));
    }

    // ── 5. MARK USER AS LEFT ──────────────────────────────────────────
    @PostMapping("/users/{id}/leave")
    public ResponseEntity<?> markUserAsLeft(@PathVariable Long id, @RequestParam String leavingDate) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.badRequest().body("Active user profile not found!");

        User user = userOpt.get();
        LeftUser leftUser = new LeftUser(user, LocalDate.parse(leavingDate));
        leftUserRepository.save(leftUser);
        userRepository.delete(user);

        audit("USER_DELETED", "admin", user.getUsername(),
                user.getRole() + " " + user.getUsername() + " marked as left on " + leavingDate);

        return ResponseEntity.ok(Map.of("status", "SUCCESS",
                "message", "User successfully archived as left. Account deactivated."));
    }

    // ── 6. GET LEFT USERS ─────────────────────────────────────────────
    @GetMapping("/users/left")
    public ResponseEntity<List<LeftUser>> getLeftUsers() {
        return ResponseEntity.ok(leftUserRepository.findAll());
    }

    // ── 7. GET ACTIVE STUDENTS ────────────────────────────────────────
    @GetMapping("/users/active/students")
    public ResponseEntity<List<User>> getActiveStudents() {
        return ResponseEntity.ok(userRepository.findByRole("STUDENT"));
    }

    // ── 8. GET ACTIVE TEACHERS ────────────────────────────────────────
    @GetMapping("/users/active/teachers")
    public ResponseEntity<List<User>> getActiveTeachers() {
        return ResponseEntity.ok(userRepository.findByRole("TEACHER"));
    }

    // ── 9. RESET PASSWORD ─────────────────────────────────────────────
    @PutMapping("/users/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id,
                                           @RequestBody Map<String, Object> data) {
        Object adminIdObj = data.get("adminUserId");
        if (adminIdObj == null)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin ID is required.");

        Long adminId = Long.valueOf(adminIdObj.toString());
        Optional<User> adminOpt = userRepository.findById(adminId);
        if (adminOpt.isEmpty() || !adminOpt.get().getRole().equalsIgnoreCase("ADMIN"))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only admins can reset passwords.");

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.badRequest().body("User not found!");

        Object newPassObj = data.get("newPassword");
        if (newPassObj == null || newPassObj.toString().trim().length() < 4)
            return ResponseEntity.badRequest().body("Password must be at least 4 characters.");

        User user = userOpt.get();
        // Also clear any lockout when admin resets password
        user.setPassword(passwordEncoder.encode(newPassObj.toString().trim()));
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        audit("PASSWORD_RESET", adminOpt.get().getUsername(), user.getUsername(),
                "Admin reset password for " + user.getRole() + " " + user.getUsername());

        return ResponseEntity.ok(Map.of("status", "SUCCESS",
                "message", "Password for " + user.getFullName() + " has been reset successfully."));
    }
}
