package com.school.repository;

import com.school.model.VisitorStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface VisitorStatsRepository extends JpaRepository<VisitorStats, String> {

    @Transactional
    @Modifying
    @Query(value = "INSERT INTO visitor_stats (page_name, visit_count) VALUES (:pageName, 1) ON DUPLICATE KEY UPDATE visit_count = visit_count + 1", nativeQuery = true)
    void incrementCount(@Param("pageName") String pageName);
}
