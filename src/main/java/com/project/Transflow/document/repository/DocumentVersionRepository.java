package com.project.Transflow.document.repository;

import com.project.Transflow.document.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    List<DocumentVersion> findByDocument_IdOrderByVersionNumberAsc(Long documentId);
    Optional<DocumentVersion> findByDocument_IdAndVersionNumber(Long documentId, Integer versionNumber);
    Optional<DocumentVersion> findByDocument_IdAndIsFinalTrue(Long documentId);
    Optional<DocumentVersion> findFirstByDocument_IdOrderByVersionNumberDesc(Long documentId);
    List<DocumentVersion> findByDocument_Id(Long documentId);
    long countByDocument_Id(Long documentId);
    void deleteByDocument_Id(Long documentId);

    /**
     * 동일 원문(source)과 그 아래 모든 복사본 문서에 걸린 버전 번호 중 최댓값.
     * 원문·복사본 간 수동 레이어 번호를 한 줄기로 맞출 때 사용.
     */
    @Query("SELECT MAX(dv.versionNumber) FROM DocumentVersion dv "
            + "WHERE dv.document.id = :sourceId OR dv.document.sourceDocument.id = :sourceId")
    Optional<Integer> findMaxVersionNumberInSourceFamily(@Param("sourceId") Long sourceId);
}

