package com.project.Transflow.document.repository;

import com.project.Transflow.document.entity.DocumentFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentFavoriteRepository extends JpaRepository<DocumentFavorite, Long> {
    Optional<DocumentFavorite> findByUserIdAndDocumentId(Long userId, Long documentId);
    List<DocumentFavorite> findByUserId(Long userId);

    /** 목록 화면용: 주어진 문서 id 중 사용자가 찜한 문서 id만 반환 */
    @Query("SELECT df.document.id FROM DocumentFavorite df WHERE df.user.id = :userId AND df.document.id IN :documentIds")
    List<Long> findFavoriteDocumentIdsByUserIdAndDocumentIdIn(
            @Param("userId") Long userId,
            @Param("documentIds") Collection<Long> documentIds);

    boolean existsByUserIdAndDocumentId(Long userId, Long documentId);
    void deleteByUserIdAndDocumentId(Long userId, Long documentId);
    void deleteByDocument_Id(Long documentId);
}


