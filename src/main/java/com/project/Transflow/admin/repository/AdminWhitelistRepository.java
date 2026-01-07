package com.project.Transflow.admin.repository;

import com.project.Transflow.admin.entity.AdminWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminWhitelistRepository extends JpaRepository<AdminWhitelist, Long> {
    Optional<AdminWhitelist> findByEmail(String email);
    boolean existsByEmail(String email);
}

