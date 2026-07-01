package com.school.controller;

import com.school.model.Attendance;
import com.school.model.User;
import com.school.repository.AttendanceRepository;
import com.school.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/salary")
@CrossOrigin(origins = "*")
public class SalaryController {

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;

    @Autowired
    public SalaryController(UserRepository userRepository, AttendanceRepository attendanceRepository) {
        this.userRepository = userRepository;
        this.attendanceRepository = attendanceRepository;
    }

    // Calculate monthly salary for a specific teacher based on daily attendance
    @GetMapping("/calculate/{teacherId}")
    public ResponseEntity<?> calculateSalary(
            @PathVariable Long teacherId,
            @RequestParam int year,
            @RequestParam int month) {

        // 1. Fetch teacher details from users table
        Optional<User> teacherOpt = userRepository.findById(teacherId);
        if (teacherOpt.isEmpty() || !teacherOpt.get().getRole().equalsIgnoreCase("TEACHER")) {
            return ResponseEntity.badRequest().body("Teacher not found!");
        }

        User teacher = teacherOpt.get();
        Double dailyRate = teacher.getBaseSalaryPerDay();
        if (dailyRate == null) {
            dailyRate = 0.0; // Default if daily rate was not set
        }

        // 2. Determine start and end date of the month
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        // 3. Retrieve attendance records in that date range for this teacher
        List<Attendance> monthlyAttendance = attendanceRepository.findByUserIdAndDateBetween(
                teacherId, startDate, endDate
        );

        // 4. Count the number of days marked PRESENT, ABSENT, or LEAVE
        long presentCount = 0;
        long absentCount = 0;
        long leaveCount = 0;

        for (Attendance record : monthlyAttendance) {
            if (record.getStatus().equalsIgnoreCase("PRESENT")) {
                presentCount++;
            } else if (record.getStatus().equalsIgnoreCase("ABSENT")) {
                absentCount++;
            } else if (record.getStatus().equalsIgnoreCase("LEAVE")) {
                leaveCount++;
            }
        }

        // 5. Calculate final net salary payout
        double netSalary = presentCount * dailyRate;

        // 6. Return calculation details in JSON
        Map<String, Object> result = new HashMap<>();
        result.put("teacherName", teacher.getFullName());
        result.put("dailyRate", dailyRate);
        result.put("month", month);
        result.put("year", year);
        result.put("presentDays", presentCount);
        result.put("absentDays", absentCount);
        result.put("leaveDays", leaveCount);
        result.put("netSalary", netSalary);

        return ResponseEntity.ok(result);
    }
}
