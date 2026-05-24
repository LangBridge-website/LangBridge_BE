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

    /** 진행률용: ORIGINAL 버전 HTML만 배치 조회 */
    @Query("SELECT dv.document.id, dv.content FROM DocumentVersion dv WHERE dv.document.id IN :documentIds AND dv.versionType = 'ORIGINAL'")
    List<Object[]> findOriginalContentByDocumentIds(@Param("documentIds") List<Long> documentIds);

    /** 문서 ID별 버전 개수 배치 조회 (목록용) */
    @Query("SELECT dv.document.id, COUNT(dv) FROM DocumentVersion dv WHERE dv.document.id IN :documentIds GROUP BY dv.document.id")
    List<Object[]> countVersionsGroupedByDocumentId(@Param("documentIds") List<Long> documentIds);
}

