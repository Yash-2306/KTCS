package com.school.repository;

import com.school.model.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    
    // Fetch a user's entire attendance history
    List<Attendance> findByUserId(Long userId);

    // Fetch all attendance records for a specific date
    List<Attendance> findByDate(LocalDate date);

    // Find if a specific user already has attendance for a specific date (helps avoid duplicates)
    Optional<Attendance> findByUserIdAndDate(Long userId, LocalDate date);

    // Fetch attendance for a user within a date range (used for monthly salary and monthly percentage)
    List<Attendance> findByUserIdAndDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
}
