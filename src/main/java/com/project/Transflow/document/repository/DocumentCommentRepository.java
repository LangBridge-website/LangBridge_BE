package com.project.Transflow.document.repository;

import com.project.Transflow.document.entity.DocumentComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentCommentRepository extends JpaRepository<DocumentComment, Long> {
    List<DocumentComment> findByDocument_IdOrderByCreatedAtAsc(Long documentId);
}
