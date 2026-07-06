package com.school.repository;

import com.school.model.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Most recent N entries for admin dashboard */
    List<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    List<AuditLog> findByPerformedByOrderByTimestampDesc(String performedBy);
}
