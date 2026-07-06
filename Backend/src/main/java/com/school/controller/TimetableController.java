package com.school.controller;

import com.school.model.TimetableEntry;
import com.school.repository.TimetableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TimetableController — admin manages class timetables.
 * Students and teachers view the schedule for a given class and section.
 *
 * API endpoints:
 *   POST   /api/timetable               — Admin: add a period slot
 *   GET    /api/timetable/{class}/{sec} — Get full week timetable for a class
 *   DELETE /api/timetable/{id}          — Admin: delete a slot
 *   PUT    /api/timetable/{id}          — Admin: update a slot
 */
@RestController
@RequestMapping("/api/timetable")
@CrossOrigin(origins = "*")
public class TimetableController {

    private final TimetableRepository timetableRepository;

    @Autowired
    public TimetableController(TimetableRepository timetableRepository) {
        this.timetableRepository = timetableRepository;
    }

    /** Admin: create a timetable entry */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody TimetableEntry entry) {
        if (entry.getClassName() == null || entry.getSection() == null
                || entry.getDayOfWeek() == null || entry.getSubject() == null
                || entry.getPeriodNumber() == null) {
            return ResponseEntity.badRequest().body("className, section, dayOfWeek, periodNumber and subject are required.");
        }
        entry.setDayOfWeek(entry.getDayOfWeek().toUpperCase());
        return ResponseEntity.ok(timetableRepository.save(entry));
    }

    /** Get full week timetable for a class/section */
    @GetMapping("/{className}/{section}")
    public ResponseEntity<List<TimetableEntry>> getTimetable(
            @PathVariable String className,
            @PathVariable String section) {
        return ResponseEntity.ok(
            timetableRepository.findByClassNameAndSectionOrderByDayOfWeekAscPeriodNumberAsc(className, section)
        );
    }

    /** Get today's timetable for a class/section/day */
    @GetMapping("/{className}/{section}/{day}")
    public ResponseEntity<List<TimetableEntry>> getDay(
            @PathVariable String className,
            @PathVariable String section,
            @PathVariable String day) {
        return ResponseEntity.ok(
            timetableRepository.findByClassNameAndSectionAndDayOfWeekOrderByPeriodNumberAsc(
                    className, section, day.toUpperCase())
        );
    }

    /** Admin: update a timetable entry */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody TimetableEntry updated) {
        Optional<TimetableEntry> opt = timetableRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        TimetableEntry entry = opt.get();
        if (updated.getSubject() != null) entry.setSubject(updated.getSubject());
        if (updated.getTeacherName() != null) entry.setTeacherName(updated.getTeacherName());
        if (updated.getStartTime() != null) entry.setStartTime(updated.getStartTime());
        if (updated.getEndTime() != null) entry.setEndTime(updated.getEndTime());
        if (updated.getPeriodNumber() != null) entry.setPeriodNumber(updated.getPeriodNumber());

        return ResponseEntity.ok(timetableRepository.save(entry));
    }

    /** Admin: delete a timetable entry */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!timetableRepository.existsById(id)) return ResponseEntity.notFound().build();
        timetableRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("status", "DELETED"));
    }

    /** Admin: get all timetable entries (for full management view) */
    @GetMapping
    public ResponseEntity<List<TimetableEntry>> getAll() {
        return ResponseEntity.ok(timetableRepository.findAll());
    }
}
