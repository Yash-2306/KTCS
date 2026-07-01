package com.school.repository;

import com.school.model.Mark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarkRepository extends JpaRepository<Mark, Long> {
    
    // Find all marks scored by a student
    List<Mark> findByStudentId(Long studentId);

    // Find a student's marks for a specific exam type
    List<Mark> findByStudentIdAndExamType(Long studentId, String examType);
}
