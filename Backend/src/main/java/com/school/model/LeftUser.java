package com.school.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.LocalDate;

@Entity
@Table(name = "left_users")
public class LeftUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String role; // TEACHER, STUDENT

    private String fullName;
    private String phone;

    // Student fields
    private String className;
    private String section;

    // Teacher fields
    private Double baseSalaryPerDay;

    // Dates
    private LocalDate joiningDate;
    private LocalDate leavingDate;

    // Default Constructor
    public LeftUser() {
    }

    // Parameterized Constructor copying from User entity
    public LeftUser(User user, LocalDate leavingDate) {
        this.username = user.getUsername();
        this.role = user.getRole();
        this.fullName = user.getFullName();
        this.phone = user.getPhone();
        this.className = user.getClassName();
        this.section = user.getSection();
        this.baseSalaryPerDay = user.getBaseSalaryPerDay();
        this.joiningDate = user.getJoiningDate();
        this.leavingDate = leavingDate;
    }

    // --- GETTERS AND SETTERS ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public Double getBaseSalaryPerDay() {
        return baseSalaryPerDay;
    }

    public void setBaseSalaryPerDay(Double baseSalaryPerDay) {
        this.baseSalaryPerDay = baseSalaryPerDay;
    }

    public LocalDate getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(LocalDate joiningDate) {
        this.joiningDate = joiningDate;
    }

    public LocalDate getLeavingDate() {
        return leavingDate;
    }

    public void setLeavingDate(LocalDate leavingDate) {
        this.leavingDate = leavingDate;
    }
}
