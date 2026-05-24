package com.project.Transflow.review.repository;

import com.project.Transflow.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByDocument_Id(Long documentId);
    List<Review> findByDocumentVersion_Id(Long documentVersionId);
    List<Review> findByReviewer_Id(Long reviewerId);
    List<Review> findByStatus(String status);

    @Query("SELECT r FROM Review r JOIN FETCH r.document LEFT JOIN FETCH r.reviewer WHERE r.status = :status")
    List<Review> findByStatusWithDocumentAndReviewer(@Param("status") String status);

    @Query("SELECT r FROM Review r JOIN FETCH r.documentVersion dv LEFT JOIN FETCH dv.createdBy "
            + "WHERE r.document.id = :documentId AND r.status = 'PENDING'")
    List<Review> findPendingWithVersionAuthorByDocumentId(@Param("documentId") Long documentId);

    List<Review> findByDocument_IdAndStatus(Long documentId, String status);
    Optional<Review> findByDocument_IdAndDocumentVersion_Id(Long documentId, Long documentVersionId);

    Optional<Review> findTopByDocument_IdAndStatusOrderByFinalApprovalAtDesc(Long documentId, String status);

    void deleteByDocument_Id(Long documentId);
}

