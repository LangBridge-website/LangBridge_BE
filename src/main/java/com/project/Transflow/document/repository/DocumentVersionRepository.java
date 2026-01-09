package com.project.Transflow.document.repository;

import com.project.Transflow.document.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

