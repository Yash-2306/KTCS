package com.school.controller;

import com.school.model.VisitorStats;
import com.school.repository.VisitorStatsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/visitors")
@CrossOrigin(origins = "*")
public class VisitorStatsController {

    private final VisitorStatsRepository repository;

    @Autowired
    public VisitorStatsController(VisitorStatsRepository repository) {
        this.repository = repository;
    }

    // 1. INCREMENT VISIT COUNT (MAIN_SITE or DASHBOARDS)
    @PostMapping("/hit")
    public ResponseEntity<Map<String, Object>> recordHit(@RequestParam String page) {
        String key = page.toUpperCase();
        if (!key.equals("MAIN_SITE") && !key.equals("DASHBOARDS")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            repository.incrementCount(key);
            VisitorStats updated = repository.findById(key)
                    .orElse(new VisitorStats(key, 1L));

            Map<String, Object> response = new HashMap<>();
            response.put("page", key);
            response.put("count", updated.getVisitCount());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // 2. GET CUMULATIVE STATS FOR ADMIN
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        Map<String, Long> response = new HashMap<>();
        
        VisitorStats mainSite = repository.findById("MAIN_SITE").orElse(new VisitorStats("MAIN_SITE", 0L));
        VisitorStats dashboards = repository.findById("DASHBOARDS").orElse(new VisitorStats("DASHBOARDS", 0L));

        response.put("mainSite", mainSite.getVisitCount());
        response.put("dashboards", dashboards.getVisitCount());
        response.put("total", mainSite.getVisitCount() + dashboards.getVisitCount());

        return ResponseEntity.ok(response);
    }
}
