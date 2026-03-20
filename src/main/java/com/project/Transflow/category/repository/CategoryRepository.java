package com.project.Transflow.category.repository;

import com.project.Transflow.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name);

    Optional<Category> findByCode(String code);
    boolean existsByCode(String code);

    boolean existsByName(String name);
}

