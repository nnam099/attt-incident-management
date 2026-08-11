package com.attt.incident.repository;

import com.attt.incident.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.enabled = true")
    java.util.List<User> findByRoleName(@org.springframework.data.repository.query.Param("roleName") com.attt.incident.entity.RoleName roleName);
}
