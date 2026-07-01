package com.school.repository;

import com.school.model.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {
    // JpaRepository gives us default methods: save(), findAll(), findById(), deleteById()
    // We can add custom search queries here later if needed!
}
