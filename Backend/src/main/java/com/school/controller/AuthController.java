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
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;

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

    // ── 10. EXCEL BULK IMPORT (Students & Teachers with Auto Roll No & Sorting) ──
    /**
     * Upload an .xlsx file containing student/teacher data.
     * Supported columns: full_name, username, password, role, class_name, section,
     * father_name, mother_name, phone, phone2, designation, base_salary_per_day
     *
     * Students are automatically sorted alphabetically by full name, and roll numbers
     * are auto-assigned (1, 2, 3...) per class and section.
     */
    @PostMapping("/users/excel-import")
    public ResponseEntity<?> excelImport(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Excel file is empty."));

        List<Map<String, String>> created = new ArrayList<>();
        List<Map<String, String>> failed  = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Excel sheet has no header row."));
            }

            Map<String, Integer> colIndex = new HashMap<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                if (cell != null) {
                    colIndex.put(cell.getStringCellValue().trim().toLowerCase().replace(" ", "_"), c);
                }
            }

            List<Map<String, String>> rawRows = new ArrayList<>();
            DataFormatter fmt = new DataFormatter();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> data = new HashMap<>();
                for (Map.Entry<String, Integer> entry : colIndex.entrySet()) {
                    Cell cell = row.getCell(entry.getValue());
                    data.put(entry.getKey(), cell != null ? fmt.formatCellValue(cell).trim() : "");
                }

                if (data.getOrDefault("username", "").isEmpty() &&
                    data.getOrDefault("full_name", "").isEmpty()) {
                    continue;
                }
                rawRows.add(data);
            }

            // Separate students and non-students
            List<Map<String, String>> studentRows = new ArrayList<>();
            List<Map<String, String>> otherRows = new ArrayList<>();

            for (Map<String, String> row : rawRows) {
                String role = row.getOrDefault("role", "STUDENT").trim().toUpperCase();
                if ("STUDENT".equals(role)) {
                    studentRows.add(row);
                } else {
                    otherRows.add(row);
                }
            }

            // Sort students alphabetically by full_name
            studentRows.sort(Comparator.comparing(
                    d -> d.getOrDefault("full_name", "").toLowerCase()));

            // Sort teachers / other roles alphabetically by full_name
            otherRows.sort(Comparator.comparing(
                    d -> d.getOrDefault("full_name", "").toLowerCase()));

            // Track roll number counters per Class-Section
            Map<String, Integer> rollCounters = new LinkedHashMap<>();

            // Process students with auto roll-numbers
            for (Map<String, String> data : studentRows) {
                processAndSaveUser(data, "STUDENT", rollCounters, created, failed);
            }

            // Process teachers / admins
            for (Map<String, String> data : otherRows) {
                String role = data.getOrDefault("role", "TEACHER").trim().toUpperCase();
                processAndSaveUser(data, role, rollCounters, created, failed);
            }

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to parse Excel file: " + e.getMessage()));
        }

        audit("EXCEL_IMPORT", "admin", null,
                "Excel import completed: " + created.size() + " created, " + failed.size() + " failed.");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCreated", created.size());
        summary.put("totalFailed", failed.size());
        summary.put("created", created);
        summary.put("failed", failed);
        return ResponseEntity.ok(summary);
    }

    private void processAndSaveUser(Map<String, String> data,
                                    String defaultRole,
                                    Map<String, Integer> rollCounters,
                                    List<Map<String, String>> created,
                                    List<Map<String, String>> failed) {
        String username = data.getOrDefault("username", "");
        String fullName = data.getOrDefault("full_name", "");
        String password = data.getOrDefault("password", "");
        String role     = data.getOrDefault("role", defaultRole).toUpperCase();
        String className= data.getOrDefault("class_name", "");
        String section  = data.getOrDefault("section", "");
        String phone    = data.getOrDefault("phone", "");
        String phone2   = data.getOrDefault("phone2", "");
        String father   = data.getOrDefault("father_name", "");
        String mother   = data.getOrDefault("mother_name", "");
        String desig    = data.getOrDefault("designation", "");
        String salaryStr= data.getOrDefault("base_salary_per_day", "");

        try {
            if (username.isEmpty() || password.isEmpty()) {
                Map<String, String> err = new HashMap<>();
                err.put("username", username.isEmpty() ? "(missing)" : username);
                err.put("fullName", fullName);
                err.put("error", "Username and password are required.");
                failed.add(err);
                return;
            }

            if (userRepository.findByUsername(username).isPresent()) {
                Map<String, String> err = new HashMap<>();
                err.put("username", username);
                err.put("fullName", fullName);
                err.put("error", "Username already exists in database.");
                failed.add(err);
                return;
            }

            User u = new User();
            u.setUsername(username);
            u.setFullName(fullName);
            u.setPassword(passwordEncoder.encode(password));
            u.setRole(role);
            u.setClassName(className.isEmpty() ? null : className);
            u.setSection(section.isEmpty() ? null : section);
            u.setPhone(phone.isEmpty() ? null : phone);
            u.setPhone2(phone2.isEmpty() ? null : phone2);
            u.setFatherName(father.isEmpty() ? null : father);
            u.setMotherName(mother.isEmpty() ? null : mother);
            u.setDesignation(desig.isEmpty() ? null : desig);
            u.setJoiningDate(LocalDate.now());

            if (!salaryStr.isEmpty()) {
                try {
                    u.setBaseSalaryPerDay(Double.valueOf(salaryStr));
                } catch (Exception ignored) {}
            }

            if ("STUDENT".equals(role)) {
                String classKey = (className != null ? className.trim() : "") + "-" + (section != null ? section.trim() : "");
                int roll = rollCounters.getOrDefault(classKey, 0) + 1;
                rollCounters.put(classKey, roll);
                u.setRollNumber(roll);
            }

            userRepository.save(u);

            Map<String, String> ok = new HashMap<>();
            ok.put("username", username);
            ok.put("fullName", fullName);
            ok.put("role", role);
            if (u.getRollNumber() != null) {
                ok.put("rollNumber", String.valueOf(u.getRollNumber()));
            }
            created.add(ok);

        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("username", username);
            err.put("fullName", fullName);
            err.put("error", e.getMessage());
            failed.add(err);
        }
    }

    // ── 11. EXCEL EXPORT (Download all Students & Teachers into .xlsx) ──
    @GetMapping("/users/excel-export")
    public ResponseEntity<byte[]> excelExport() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            // Header styling
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{30, 58, 95}, null)); // Navy Blue
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // ── Sheet 1: Students ──
            XSSFSheet studentSheet = workbook.createSheet("Students");
            String[] studentHeaders = {
                "serial_no", "roll_number", "full_name", "username", "password",
                "role", "class_name", "section", "father_name", "mother_name",
                "phone", "phone2", "designation", "joining_date"
            };

            Row sHeader = studentSheet.createRow(0);
            for (int i = 0; i < studentHeaders.length; i++) {
                Cell c = sHeader.createCell(i);
                c.setCellValue(studentHeaders[i]);
                c.setCellStyle(headerStyle);
                studentSheet.setColumnWidth(i, 4500);
            }

            List<User> students = userRepository.findByRole("STUDENT");
            students.sort(Comparator.comparing(
                    u -> (u.getFullName() != null ? u.getFullName().toLowerCase() : "")));

            int sIdx = 1;
            Map<String, Integer> rollMap = new HashMap<>();
            for (User s : students) {
                Row r = studentSheet.createRow(sIdx);
                String key = (s.getClassName() != null ? s.getClassName() : "") + "-" + (s.getSection() != null ? s.getSection() : "");
                int roll = rollMap.getOrDefault(key, 0) + 1;
                rollMap.put(key, roll);

                r.createCell(0).setCellValue(sIdx);
                r.createCell(1).setCellValue(s.getRollNumber() != null ? s.getRollNumber() : roll);
                r.createCell(2).setCellValue(nvl(s.getFullName()));
                r.createCell(3).setCellValue(nvl(s.getUsername()));
                r.createCell(4).setCellValue("[re-enter password]");
                r.createCell(5).setCellValue("STUDENT");
                r.createCell(6).setCellValue(nvl(s.getClassName()));
                r.createCell(7).setCellValue(nvl(s.getSection()));
                r.createCell(8).setCellValue(nvl(s.getFatherName()));
                r.createCell(9).setCellValue(nvl(s.getMotherName()));
                r.createCell(10).setCellValue(nvl(s.getPhone()));
                r.createCell(11).setCellValue(nvl(s.getPhone2()));
                r.createCell(12).setCellValue(nvl(s.getDesignation()));
                r.createCell(13).setCellValue(s.getJoiningDate() != null ? s.getJoiningDate().toString() : "");
                sIdx++;
            }

            // ── Sheet 2: Teachers ──
            XSSFSheet teacherSheet = workbook.createSheet("Teachers");
            String[] teacherHeaders = {
                "serial_no", "full_name", "username", "password", "role",
                "phone", "phone2", "designation", "base_salary_per_day", "joining_date"
            };

            Row tHeader = teacherSheet.createRow(0);
            for (int i = 0; i < teacherHeaders.length; i++) {
                Cell c = tHeader.createCell(i);
                c.setCellValue(teacherHeaders[i]);
                c.setCellStyle(headerStyle);
                teacherSheet.setColumnWidth(i, 4500);
            }

            List<User> teachers = userRepository.findByRole("TEACHER");
            teachers.sort(Comparator.comparing(
                    u -> (u.getFullName() != null ? u.getFullName().toLowerCase() : "")));

            int tIdx = 1;
            for (User t : teachers) {
                Row r = teacherSheet.createRow(tIdx);
                r.createCell(0).setCellValue(tIdx);
                r.createCell(1).setCellValue(nvl(t.getFullName()));
                r.createCell(2).setCellValue(nvl(t.getUsername()));
                r.createCell(3).setCellValue("[re-enter password]");
                r.createCell(4).setCellValue("TEACHER");
                r.createCell(5).setCellValue(nvl(t.getPhone()));
                r.createCell(6).setCellValue(nvl(t.getPhone2()));
                r.createCell(7).setCellValue(nvl(t.getDesignation()));
                r.createCell(8).setCellValue(t.getBaseSalaryPerDay() != null ? t.getBaseSalaryPerDay() : 0.0);
                r.createCell(9).setCellValue(t.getJoiningDate() != null ? t.getJoiningDate().toString() : "");
                tIdx++;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);

            String filename = "KTCS_Users_Export_" + LocalDate.now() + ".xlsx";
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + filename)
                    .contentType(org.springframework.http.MediaType
                            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(baos.toByteArray());

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }
}
