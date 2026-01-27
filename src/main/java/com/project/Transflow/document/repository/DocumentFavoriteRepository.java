package com.project.Transflow.document.repository;

import com.project.Transflow.document.entity.DocumentFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentFavoriteRepository extends JpaRepository<DocumentFavorite, Long> {
    Optional<DocumentFavorite> findByUserIdAndDocumentId(Long userId, Long documentId);
    List<DocumentFavorite> findByUserId(Long userId);
    boolean existsByUserIdAndDocumentId(Long userId, Long documentId);
    void deleteByUserIdAndDocumentId(Long userId, Long documentId);
}


