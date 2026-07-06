package com.school.controller;

import com.school.model.Announcement;
import com.school.repository.AnnouncementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AnnouncementController — admin creates/deletes announcements.
 * Teachers and students can fetch announcements targeted at their role.
 *
 * API endpoints:
 *   POST   /api/announcements            — Admin: create announcement
 *   GET    /api/announcements            — Admin: get all
 *   GET    /api/announcements/for/{role} — Teacher/Student: get their announcements
 *   DELETE /api/announcements/{id}       — Admin: delete announcement
 */
@RestController
@RequestMapping("/api/announcements")
@CrossOrigin(origins = "*")
public class AnnouncementController {

    private final AnnouncementRepository announcementRepository;

    @Autowired
    public AnnouncementController(AnnouncementRepository announcementRepository) {
        this.announcementRepository = announcementRepository;
    }

    /** Admin creates a new announcement */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "").trim();
        String content = body.getOrDefault("content", "").trim();
        String targetRole = body.getOrDefault("targetRole", "ALL").trim().toUpperCase();
        String createdBy = body.getOrDefault("createdBy", "admin").trim();

        if (title.isEmpty() || content.isEmpty()) {
            return ResponseEntity.badRequest().body("Title and content are required.");
        }
        if (!List.of("ALL", "TEACHER", "STUDENT").contains(targetRole)) {
            targetRole = "ALL";
        }

        Announcement a = new Announcement();
        a.setTitle(title);
        a.setContent(content);
        a.setTargetRole(targetRole);
        a.setCreatedBy(createdBy);
        a.setCreatedAt(LocalDateTime.now());

        return ResponseEntity.ok(announcementRepository.save(a));
    }

    /** Admin: get all announcements newest first */
    @GetMapping
    public ResponseEntity<List<Announcement>> getAll() {
        return ResponseEntity.ok(announcementRepository.findAllByOrderByCreatedAtDesc());
    }

    /** Teacher / Student: get announcements for their role (+ ALL) */
    @GetMapping("/for/{role}")
    public ResponseEntity<List<Announcement>> getForRole(@PathVariable String role) {
        List<Announcement> list = announcementRepository.findByTargetRoleOrAll(role.toUpperCase());
        // Return at most 20 most recent
        if (list.size() > 20) list = list.subList(0, 20);
        return ResponseEntity.ok(list);
    }

    /** Admin: delete announcement */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!announcementRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        announcementRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("status", "DELETED"));
    }
}
