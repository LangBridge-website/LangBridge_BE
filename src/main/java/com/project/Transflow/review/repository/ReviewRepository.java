package com.project.Transflow.review.repository;

import com.project.Transflow.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByDocument_Id(Long documentId);
    List<Review> findByDocumentVersion_Id(Long documentVersionId);
    List<Review> findByReviewer_Id(Long reviewerId);
    List<Review> findByStatus(String status);
    List<Review> findByDocument_IdAndStatus(Long documentId, String status);
    Optional<Review> findByDocument_IdAndDocumentVersion_Id(Long documentId, Long documentVersionId);
}

