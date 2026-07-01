package com.school.repository;

import com.school.model.LeftUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeftUserRepository extends JpaRepository<LeftUser, Long> {
}
