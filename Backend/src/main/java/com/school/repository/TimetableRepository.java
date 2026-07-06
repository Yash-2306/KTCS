package com.school.repository;

import com.school.model.TimetableEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TimetableRepository extends JpaRepository<TimetableEntry, Long> {

    List<TimetableEntry> findByClassNameAndSectionOrderByDayOfWeekAscPeriodNumberAsc(
            String className, String section);

    List<TimetableEntry> findByClassNameAndSectionAndDayOfWeekOrderByPeriodNumberAsc(
            String className, String section, String dayOfWeek);
}
