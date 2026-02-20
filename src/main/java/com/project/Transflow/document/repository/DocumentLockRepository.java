package com.project.Transflow.document.repository;

import com.project.Transflow.document.entity.DocumentLock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentLockRepository extends JpaRepository<DocumentLock, Long> {
    // 단순 조회 (findFirst로 LIMIT 1 적용 - 중복 락 시 NonUniqueResultException 방지)
    Optional<DocumentLock> findFirstByDocument_Id(Long documentId);
    
    // 락 상태 조회용 (LAZY 로딩, Pageable로 LIMIT 1 적용 - 중복 락 시 NonUniqueResultException 방지)
    @Query("SELECT dl FROM DocumentLock dl " +
           "LEFT JOIN FETCH dl.lockedBy " +
           "LEFT JOIN FETCH dl.document " +
           "WHERE dl.document.id = :documentId ORDER BY dl.id DESC")
    List<DocumentLock> findByDocumentIdWithUserList(@Param("documentId") Long documentId, Pageable pageable);
    
    @Modifying
    @Query("DELETE FROM DocumentLock dl WHERE dl.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);

    // 삭제 건수를 반환하는 버전 (스테일 락 정리 시 사용)
    @Modifying
    @Query("DELETE FROM DocumentLock dl WHERE dl.document.id = :documentId")
    int deleteAllByDocumentId(@Param("documentId") Long documentId);
    
    boolean existsByDocumentId(Long documentId);
}

