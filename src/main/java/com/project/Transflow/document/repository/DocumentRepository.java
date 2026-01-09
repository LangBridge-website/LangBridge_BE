package com.project.Transflow.document.repository;

import com.project.Transflow.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByStatus(String status);
    List<Document> findByCategoryId(Long categoryId);
    List<Document> findByCreatedBy_Id(Long createdById);
    List<Document> findByStatusAndCategoryId(String status, Long categoryId);
    Optional<Document> findByIdAndStatus(Long id, String status);
}

