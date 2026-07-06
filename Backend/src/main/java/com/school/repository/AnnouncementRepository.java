package com.school.repository;

import com.school.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /** Fetch announcements for a role — returns both role-specific AND "ALL" announcements */
    @Query("SELECT a FROM Announcement a WHERE a.targetRole = 'ALL' OR a.targetRole = :role ORDER BY a.createdAt DESC")
    List<Announcement> findByTargetRoleOrAll(String role);

    /** All announcements newest first (for admin view) */
    List<Announcement> findAllByOrderByCreatedAtDesc();
}
