package com.school.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * User — represents Admin, Teacher, or Student.
 *
 * Changes from v1:
 *  - Added @Table indexes on role + username for fast lookups with 300+ students
 *  - Added loginAttempts + lockedUntil for account lockout after 5 failed attempts
 *  - Interview: "We added DB indexes on the role column so filtering students
 *    from 300+ users doesn't do a full table scan each time."
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_role", columnList = "role"),
    @Index(name = "idx_users_username", columnList = "username"),
    @Index(name = "idx_users_class_section", columnList = "className, section")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    // WRITE_ONLY = Jackson will READ this from incoming JSON (e.g. registration form)
    //              but will NEVER include it in outgoing API responses.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role; // ADMIN, TEACHER, STUDENT

    private String fullName;
    private String phone;

    // Student fields
    private String className;
    private String section;

    // Teacher fields
    private Double baseSalaryPerDay;

    // Joining & Leaving dates
    private LocalDate joiningDate;
    private LocalDate leavingDate;

    // ── Account Lockout (security — prevent brute force) ──────────────
    // After 5 consecutive wrong passwords, account locks for 15 minutes.
    // Interview: "We track failed login attempts on the User entity. After 5
    // failures, we set lockedUntil = now + 15 min. The login endpoint checks
    // this field before attempting password verification."
    @Column(nullable = false)
    private int loginAttempts = 0;

    @Column
    private LocalDateTime lockedUntil; // null = not locked

    // ── Constructors ──────────────────────────────────────────────────

    public User() {}

    public User(String username, String password, String role, String fullName, String phone) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.fullName = fullName;
        this.phone = phone;
    }

    // ── Getters & Setters ─────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public Double getBaseSalaryPerDay() { return baseSalaryPerDay; }
    public void setBaseSalaryPerDay(Double baseSalaryPerDay) { this.baseSalaryPerDay = baseSalaryPerDay; }

    public LocalDate getJoiningDate() { return joiningDate; }
    public void setJoiningDate(LocalDate joiningDate) { this.joiningDate = joiningDate; }

    public LocalDate getLeavingDate() { return leavingDate; }
    public void setLeavingDate(LocalDate leavingDate) { this.leavingDate = leavingDate; }

    public int getLoginAttempts() { return loginAttempts; }
    public void setLoginAttempts(int loginAttempts) { this.loginAttempts = loginAttempts; }

    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }
}
