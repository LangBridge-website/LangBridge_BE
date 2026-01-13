package com.project.Transflow.document.repository;

import com.project.Transflow.document.entity.DocumentLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentLockRepository extends JpaRepository<DocumentLock, Long> {
    Optional<DocumentLock> findByDocumentId(Long documentId);
    void deleteByDocumentId(Long documentId);
    boolean existsByDocumentId(Long documentId);
}

