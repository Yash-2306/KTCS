package com.school.controller;

import com.school.model.AuditLog;
import com.school.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AuditLogController — admin views the audit trail.
 *
 * API endpoints:
 *   GET /api/audit?limit=50  — Most recent N audit entries
 *   GET /api/audit/user/{username} — All actions by a specific user
 */
@RestController
@RequestMapping("/api/audit")
@CrossOrigin(origins = "*")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @Autowired
    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** Get most recent audit entries (default 100, max 500) */
    @GetMapping
    public ResponseEntity<List<AuditLog>> getRecent(
            @RequestParam(defaultValue = "100") int limit) {
        if (limit > 500) limit = 500;
        return ResponseEntity.ok(
            auditLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, limit))
        );
    }

    /** Get all audit actions performed by a specific user */
    @GetMapping("/user/{username}")
    public ResponseEntity<List<AuditLog>> getByUser(@PathVariable String username) {
        return ResponseEntity.ok(
            auditLogRepository.findByPerformedByOrderByTimestampDesc(username)
        );
    }
}
