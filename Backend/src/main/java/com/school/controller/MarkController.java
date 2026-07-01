package com.school.controller;

import com.school.model.Mark;
import com.school.model.User;
import com.school.repository.MarkRepository;
import com.school.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/marks")
@CrossOrigin(origins = "*")
public class MarkController {

    private final MarkRepository markRepository;
    private final UserRepository userRepository;

    @Autowired
    public MarkController(MarkRepository markRepository, UserRepository userRepository) {
        this.markRepository = markRepository;
        this.userRepository = userRepository;
    }

    // 1. Submit marks for a student (takes a list of subject marks)
    @PostMapping("/submit")
    public ResponseEntity<?> submitMarks(@RequestBody List<Mark> marksList) {
        List<Mark> savedMarks = new ArrayList<>();

        for (Mark newMark : marksList) {
            // Check if mark already exists for this student, subject, and exam type to update
            List<Mark> existingMarks = markRepository.findByStudentId(newMark.getStudentId());
            Optional<Mark> existing = existingMarks.stream()
                    .filter(m -> m.getSubject().equalsIgnoreCase(newMark.getSubject())
                            && m.getExamType().equalsIgnoreCase(newMark.getExamType()))
                    .findFirst();

            Mark mark;
            if (existing.isPresent()) {
                mark = existing.get();
                mark.setMarksObtained(newMark.getMarksObtained());
                mark.setMaxMarks(newMark.getMaxMarks());
            } else {
                mark = newMark;
            }
            savedMarks.add(markRepository.save(mark));
        }

        return ResponseEntity.ok(savedMarks);
    }

    // 2. Generate Report Card data for a student
    @GetMapping("/student/{studentId}/report-card")
    public ResponseEntity<?> generateReportCard(
            @PathVariable Long studentId,
            @RequestParam(required = false) String examType) {

        // Fetch Student Profile
        Optional<User> studentOpt = userRepository.findById(studentId);
        if (studentOpt.isEmpty() || !studentOpt.get().getRole().equalsIgnoreCase("STUDENT")) {
            return ResponseEntity.badRequest().body("Student profile not found!");
        }
        User student = studentOpt.get();

        // Fetch Marks (filtered by exam type if provided)
        List<Mark> marks;
        if (examType != null && !examType.isEmpty()) {
            marks = markRepository.findByStudentIdAndExamType(studentId, examType.toUpperCase());
        } else {
            marks = markRepository.findByStudentId(studentId);
        }

        // Calculations
        double totalObtained = 0.0;
        double totalMax = 0.0;

        for (Mark m : marks) {
            totalObtained += m.getMarksObtained();
            totalMax += m.getMaxMarks();
        }

        double percentage = 0.0;
        if (totalMax > 0.0) {
            percentage = (totalObtained / totalMax) * 100;
        }

        // Letter Grade logic
        String grade;
        if (percentage >= 90) grade = "A+";
        else if (percentage >= 80) grade = "A";
        else if (percentage >= 70) grade = "B";
        else if (percentage >= 60) grade = "C";
        else if (percentage >= 50) grade = "D";
        else grade = "F";

        String status = (percentage >= 40.0) ? "PASS" : "FAIL";

        // Build Response JSON
        Map<String, Object> reportCard = new HashMap<>();
        reportCard.put("studentName", student.getFullName());
        reportCard.put("className", student.getClassName());
        reportCard.put("section", student.getSection());
        reportCard.put("username", student.getUsername());
        reportCard.put("examType", examType != null ? examType.toUpperCase() : "ALL EXAMS");
        reportCard.put("subjects", marks);
        reportCard.put("totalObtained", totalObtained);
        reportCard.put("totalMax", totalMax);
        reportCard.put("percentage", percentage);
        reportCard.put("grade", grade);
        reportCard.put("status", status);

        return ResponseEntity.ok(reportCard);
    }
}
