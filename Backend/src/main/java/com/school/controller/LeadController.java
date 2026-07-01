package com.school.controller;

import com.school.model.Lead;
import com.school.repository.LeadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leads")
@CrossOrigin(origins = "*") // Allows connections from our HTML files
public class LeadController {

    private final LeadRepository leadRepository;

    @Autowired
    public LeadController(LeadRepository leadRepository) {
        this.leadRepository = leadRepository;
    }

    // 1. Submit a new lead (used by Contact Us page)
    @PostMapping
    public ResponseEntity<Lead> createLead(@RequestBody Lead lead) {
        Lead savedLead = leadRepository.save(lead);
        return ResponseEntity.ok(savedLead);
    }

    // 2. Get all leads (used by Admin Dashboard)
    @GetMapping
    public ResponseEntity<List<Lead>> getAllLeads() {
        List<Lead> leads = leadRepository.findAll();
        return ResponseEntity.ok(leads);
    }

    // 3. Update status of a lead (e.g., Admin marks a lead as "CONTACTED")
    @PutMapping("/{id}/status")
    public ResponseEntity<Lead> updateLeadStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        
        return leadRepository.findById(id)
                .map(lead -> {
                    lead.setStatus(status.toUpperCase());
                    Lead updated = leadRepository.save(lead);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
