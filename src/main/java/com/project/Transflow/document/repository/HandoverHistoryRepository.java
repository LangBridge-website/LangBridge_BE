package com.project.Transflow.document.repository;

import com.project.Transflow.document.entity.HandoverHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HandoverHistoryRepository extends JpaRepository<HandoverHistory, Long> {
    
    /**
     * 문서의 모든 인계 히스토리를 시간순으로 조회
     */
    List<HandoverHistory> findByDocument_IdOrderByCreatedAtDesc(Long documentId);
    
    /**
     * 문서의 최신 인계 히스토리 조회
     * findFirst 사용 시 Spring Data JPA가 LIMIT 1을 적용하여 NonUniqueResultException 방지
     */
    Optional<HandoverHistory> findFirstByDocument_IdOrderByCreatedAtDesc(Long documentId);
    
    /**
     * 특정 사용자가 인계한 히스토리 조회
     */
    List<HandoverHistory> findByHandedOverBy_IdOrderByCreatedAtDesc(Long userId);
}

