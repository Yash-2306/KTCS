package com.school.repository;

import com.school.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;


@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Find user by their username (used for login)
    Optional<User> findByUsername(String username);

    // Find all users by role (e.g., all Teachers or all Students)
    List<User> findByRole(String role);

    // Find students class-wise and section-wise (used by Teachers for attendance)
    List<User> findByRoleAndClassNameAndSection(String role, String className, String section);
}

