package com.school.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "marks", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"student_id", "subject", "exam_type"}, name = "uk_marks_student_subject_exam")
})
public class Mark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId; // Refers to the Student's User ID

    @Column(nullable = false)
    private String subject; // e.g., Mathematics, Science, English

    @Column(nullable = false)
    private String examType; // e.g., MID_TERM, FINAL, UNIT_TEST

    @Column(nullable = false)
    private Double marksObtained;

    @Column(nullable = false)
    private Double maxMarks; // e.g., 100.0

    // Default Constructor
    public Mark() {
    }

    // Parameterized Constructor
    public Mark(Long studentId, String subject, String examType, Double marksObtained, Double maxMarks) {
        this.studentId = studentId;
        this.subject = subject;
        this.examType = examType.toUpperCase();
        this.marksObtained = marksObtained;
        this.maxMarks = maxMarks;
    }

    // --- GETTERS AND SETTERS ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getExamType() {
        return examType;
    }

    public void setExamType(String examType) {
        this.examType = examType.toUpperCase();
    }

    public Double getMarksObtained() {
        return marksObtained;
    }

    public void setMarksObtained(Double marksObtained) {
        this.marksObtained = marksObtained;
    }

    public Double getMaxMarks() {
        return maxMarks;
    }

    public void setMaxMarks(Double maxMarks) {
        this.maxMarks = maxMarks;
    }
}
