package com.project.Transflow.document.repository;

import com.project.Transflow.document.entity.Document;
import org.springframework.data.domain.Pageable;
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

    /** 원문 문서 ID로 복사본 목록 조회 (다른 사람 작업물) */
    List<Document> findBySourceDocument_IdOrderByCreatedAtDesc(Long sourceDocumentId);

    /** 원문만 조회 (복사본 제외) - JOIN FETCH로 N+1 완화 */
    @Query("SELECT d FROM Document d LEFT JOIN FETCH d.createdBy LEFT JOIN FETCH d.lastModifiedBy WHERE d.sourceDocument IS NULL ORDER BY d.updatedAt DESC")
    List<Document> findSourceDocumentsWithUsers();

    /** 상태별 목록 — createdBy/lastModifiedBy JOIN FETCH */
    @Query("SELECT d FROM Document d LEFT JOIN FETCH d.createdBy LEFT JOIN FETCH d.lastModifiedBy WHERE d.status = :status")
    List<Document> findByStatusWithUsers(@Param("status") String status);

    @Query("SELECT d FROM Document d LEFT JOIN FETCH d.createdBy LEFT JOIN FETCH d.lastModifiedBy WHERE d.status = :status ORDER BY d.updatedAt DESC")
    List<Document> findTopByStatusWithUsers(@Param("status") String status, Pageable pageable);

    long countByStatus(String status);

    @Query("SELECT d FROM Document d LEFT JOIN FETCH d.createdBy cb LEFT JOIN FETCH d.lastModifiedBy lmb "
            + "WHERE d.status = 'IN_TRANSLATION' AND (cb.id = :userId OR lmb.id = :userId) ORDER BY d.updatedAt DESC")
    List<Document> findInTranslationForUser(@Param("userId") Long userId, Pageable pageable);

    /** 내가 작업 중인 복사본 (대시보드·작업 중 목록용) */
    @Query("SELECT d FROM Document d LEFT JOIN FETCH d.createdBy LEFT JOIN FETCH d.lastModifiedBy WHERE "
            + "(d.status = 'IN_TRANSLATION' AND (d.createdBy.id = :userId OR d.lastModifiedBy.id = :userId)) OR "
            + "(d.status IN ('PENDING_REVIEW', 'APPROVED', 'PUBLISHED') AND d.createdBy.id = :userId)")
    List<Document> findMyWorkingAssignments(@Param("userId") Long userId);

    @Query("SELECT d FROM Document d LEFT JOIN FETCH d.createdBy LEFT JOIN FETCH d.lastModifiedBy WHERE d.id IN :ids")
    List<Document> findByIdsWithUsers(@Param("ids") List<Long> ids);

    @Query("SELECT d FROM Document d LEFT JOIN FETCH d.createdBy LEFT JOIN FETCH d.lastModifiedBy "
            + "WHERE d.sourceDocument.id = :sourceDocumentId ORDER BY d.createdAt DESC")
    List<Document> findCopiesBySourceIdWithUsers(@Param("sourceDocumentId") Long sourceDocumentId);

    /** 원문 ID + 생성자 ID로 복사본 조회 (내가 해당 원문에서 만든 복사본이 있는지 확인) */
    Optional<Document> findBySourceDocument_IdAndCreatedBy_Id(Long sourceDocumentId, Long createdById);

    /** 원문 ID별 IN_TRANSLATION 복사본 수 (목록 인원 칸용 배치 조회) */
    @Query("SELECT d.sourceDocument.id, COUNT(d) FROM Document d WHERE d.sourceDocument.id IN :sourceIds AND d.status = 'IN_TRANSLATION' GROUP BY d.sourceDocument.id")
    List<Object[]> countInTranslationCopiesGroupedBySourceId(@Param("sourceIds") List<Long> sourceIds);

    /** 목록용: 복사본 status·작업자명만 배치 조회 (toResponse 없음) */
    @Query("SELECT d.sourceDocument.id, d.status, u.name FROM Document d LEFT JOIN d.createdBy u WHERE d.sourceDocument.id IN :sourceIds ORDER BY d.createdAt DESC")
    List<Object[]> findCopyLightMetaBySourceIds(@Param("sourceIds") List<Long> sourceIds);

    /** 현재 사용자가 IN_TRANSLATION 복사본을 가진 원문 ID */
    @Query("SELECT DISTINCT d.sourceDocument.id FROM Document d WHERE d.sourceDocument.id IN :sourceIds AND d.createdBy.id = :userId AND d.status = 'IN_TRANSLATION'")
    List<Long> findSourceIdsWhereUserHasInTranslationCopy(@Param("sourceIds") List<Long> sourceIds, @Param("userId") Long userId);
}

