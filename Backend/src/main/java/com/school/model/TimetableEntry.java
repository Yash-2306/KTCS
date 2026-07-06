package com.school.model;

import jakarta.persistence.*;

/**
 * TimetableEntry — one period slot in a class's weekly schedule.
 * Admin creates entries. Students and teachers view the schedule for their class.
 */
@Entity
@Table(name = "timetable", indexes = {
    @Index(name = "idx_timetable_class_section", columnList = "className, section")
})
public class TimetableEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String className;   // e.g. "10", "9"

    @Column(nullable = false, length = 5)
    private String section;     // e.g. "A", "B"

    @Column(nullable = false, length = 15)
    private String dayOfWeek;   // MONDAY, TUESDAY, etc.

    @Column(nullable = false)
    private Integer periodNumber; // 1–8

    @Column(nullable = false, length = 100)
    private String subject;

    @Column(length = 100)
    private String teacherName;

    @Column(length = 20)
    private String startTime;   // "8:00 AM"

    @Column(length = 20)
    private String endTime;     // "8:45 AM"

    // ── Getters & Setters ─────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public Integer getPeriodNumber() { return periodNumber; }
    public void setPeriodNumber(Integer periodNumber) { this.periodNumber = periodNumber; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
}
