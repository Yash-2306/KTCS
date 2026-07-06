package com.school.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * AuditLog — immutable record of important admin actions.
 * Every password reset, user creation, deletion, and login is logged here.
 * Interview: "We implemented an audit trail using a dedicated AuditLog table.
 * Every sensitive action by an admin is recorded with who did it, when, and what."
 */
@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_performedBy", columnList = "performedBy")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** What happened: LOGIN, LOGOUT, PASSWORD_RESET, USER_CREATED, USER_DELETED, ACCOUNT_LOCKED */
    @Column(nullable = false, length = 50)
    private String action;

    /** Username of person who performed the action */
    @Column(nullable = false, length = 100)
    private String performedBy;

    /** Username of affected user (null if action has no target, e.g. LOGIN) */
    @Column(length = 100)
    private String targetUser;

    /** Human-readable description */
    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    // ── Constructors ──────────────────────────────────────────────────

    public AuditLog() {}

    public AuditLog(String action, String performedBy, String targetUser, String details) {
        this.action = action;
        this.performedBy = performedBy;
        this.targetUser = targetUser;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public String getTargetUser() { return targetUser; }
    public void setTargetUser(String targetUser) { this.targetUser = targetUser; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
