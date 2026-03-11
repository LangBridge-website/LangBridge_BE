package com.project.Transflow.document.repository;

import com.project.Transflow.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    List<Document> findByOriginalUrl(String originalUrl);
    List<Document> findByOriginalUrlOrderByCreatedAtDesc(String originalUrl);
    
    // 제목으로 검색 (대소문자 구분 없음)
    @Query("SELECT d FROM Document d WHERE LOWER(d.title) LIKE LOWER(CONCAT('%', :title, '%'))")
    List<Document> findByTitleContainingIgnoreCase(@Param("title") String title);

    /**
     * 번역 대기 중인 원문 중, 아직 해당 원문에 대해 APPROVED/PUBLISHED 문서가 없는 것만 조회.
     * (이 원문이 "종료"되지 않았을 때만 노출)
     */
    @Query("SELECT d FROM Document d WHERE d.sourceDocument IS NULL AND d.status = 'PENDING_TRANSLATION' " +
           "AND NOT EXISTS (SELECT 1 FROM Document d2 WHERE (d2.sourceDocument = d OR d2 = d) " +
           "AND d2.status IN ('APPROVED', 'PUBLISHED'))")
    List<Document> findPendingTranslationSourcesNotFinalized();
}

