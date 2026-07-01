package com.school.controller;

import com.school.model.Attendance;
import com.school.model.User;
import com.school.repository.AttendanceRepository;
import com.school.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(origins = "*")
public class AttendanceController {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    @Autowired
    public AttendanceController(AttendanceRepository attendanceRepository, UserRepository userRepository) {
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
    }

    // 1. Get students for a specific class and section
    @GetMapping("/students")
    public ResponseEntity<List<User>> getStudents(
            @RequestParam String className,
            @RequestParam String section) {
        List<User> students = userRepository.findByRoleAndClassNameAndSection("STUDENT", className, section);
        return ResponseEntity.ok(students);
    }

    // 2. Get all teachers
    @GetMapping("/teachers")
    public ResponseEntity<List<User>> getTeachers() {
        List<User> teachers = userRepository.findByRole("TEACHER");
        return ResponseEntity.ok(teachers);
    }

    // 2b. Get all students
    @GetMapping("/students/all")
    public ResponseEntity<List<User>> getAllStudents() {
        List<User> students = userRepository.findByRole("STUDENT");
        return ResponseEntity.ok(students);
    }

    // 3. Submit attendance for a selected date (creates new or updates existing)
    //    @DateTimeFormat ensures the date string is properly parsed and returns a
    //    friendly JSON error (via GlobalExceptionHandler) if the format is wrong
    @PostMapping("/submit")
    public ResponseEntity<?> submitAttendance(
            @RequestBody List<Map<String, Object>> records,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<Attendance> savedRecords = new ArrayList<>();

        for (Map<String, Object> record : records) {
            Long userId = Long.valueOf(record.get("userId").toString());
            String status = record.get("status").toString();

            // Check if attendance already exists for this user on this date
            Optional<Attendance> existing = attendanceRepository.findByUserIdAndDate(userId, date);

            Attendance attendance;
            if (existing.isPresent()) {
                // Update the existing record
                attendance = existing.get();
                attendance.setStatus(status);
            } else {
                // Create a new record
                attendance = new Attendance(userId, date, status);
            }

            savedRecords.add(attendanceRepository.save(attendance));
        }

        return ResponseEntity.ok(savedRecords);
    }

    // 4. Get student list + attendance status for a specific class, section, and date
    @GetMapping("/status")
    public ResponseEntity<?> getStudentAttendanceStatus(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String className,
            @RequestParam String section) {

        List<User> students = userRepository.findByRoleAndClassNameAndSection("STUDENT", className, section);

        List<Map<String, Object>> result = new ArrayList<>();

        for (User student : students) {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", student.getId());
            map.put("fullName", student.getFullName());
            map.put("username", student.getUsername());

            // Check if attendance is already marked for this date
            Optional<Attendance> att = attendanceRepository.findByUserIdAndDate(student.getId(), date);
            map.put("status", att.isPresent() ? att.get().getStatus() : "NOT_MARKED");
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    // 5. Get teacher list + attendance status for a specific date
    @GetMapping("/status/teachers")
    public ResponseEntity<?> getTeacherAttendanceStatus(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<User> teachers = userRepository.findByRole("TEACHER");

        List<Map<String, Object>> result = new ArrayList<>();

        for (User teacher : teachers) {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", teacher.getId());
            map.put("fullName", teacher.getFullName());
            map.put("username", teacher.getUsername());
            map.put("baseSalaryPerDay", teacher.getBaseSalaryPerDay());

            Optional<Attendance> att = attendanceRepository.findByUserIdAndDate(teacher.getId(), date);
            map.put("status", att.isPresent() ? att.get().getStatus() : "NOT_MARKED");
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    // 6. Get full attendance history for a single user (student or teacher)
    @GetMapping("/history/{userId}")
    public ResponseEntity<List<Attendance>> getHistory(@PathVariable Long userId) {
        List<Attendance> history = attendanceRepository.findByUserId(userId);
        return ResponseEntity.ok(history);
    }
}
