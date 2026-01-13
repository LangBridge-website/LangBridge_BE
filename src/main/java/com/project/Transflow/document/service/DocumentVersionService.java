package com.project.Transflow.document.service;

import com.project.Transflow.document.dto.CreateDocumentVersionRequest;
import com.project.Transflow.document.dto.DocumentVersionResponse;
import com.project.Transflow.document.entity.Document;
import com.project.Transflow.document.entity.DocumentVersion;
import com.project.Transflow.document.repository.DocumentRepository;
import com.project.Transflow.document.repository.DocumentVersionRepository;
import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentVersionService {

    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional
    public DocumentVersionResponse createVersion(Long documentId, CreateDocumentVersionRequest request, Long createdById) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + documentId));

        // 개발 단계: createdById가 null이면 첫 번째 사용자 사용 (또는 기본 사용자)
        User createdBy;
        if (createdById != null) {
            createdBy = userRepository.findById(createdById)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + createdById));
        } else {
            // 기본 사용자 찾기 (첫 번째 사용자 또는 관리자)
            createdBy = userRepository.findAll().stream()
                    .filter(user -> user.getRoleLevel() <= 2) // 관리자 이상
                    .findFirst()
                    .orElseGet(() -> userRepository.findAll().stream()
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("시스템에 사용자가 없습니다. 먼저 사용자를 생성해주세요.")));
            log.warn("Authorization 헤더가 없어 기본 사용자 사용: {}", createdBy.getId());
        }

        // 다음 버전 번호 계산
        Integer nextVersionNumber = calculateNextVersionNumber(documentId, request.getVersionType());

        // 최종 버전 체크
        Boolean isFinal = request.getIsFinal() != null ? request.getIsFinal() : false;
        if (isFinal) {
            // 기존 최종 버전이 있으면 해제
            documentVersionRepository.findByDocument_IdAndIsFinalTrue(documentId)
                    .ifPresent(version -> version.setIsFinal(false));
        }

        DocumentVersion version = DocumentVersion.builder()
                .document(document)
                .versionNumber(nextVersionNumber)
                .versionType(request.getVersionType())
                .content(request.getContent())
                .isFinal(isFinal)
                .createdBy(createdBy)
                .build();

        DocumentVersion saved = documentVersionRepository.save(version);

        // Document의 current_version_id 업데이트
        document.setCurrentVersionId(saved.getId());
        documentRepository.save(document);

        log.info("문서 버전 생성: 문서 ID {}, 버전 번호 {}, 타입 {}", documentId, nextVersionNumber, request.getVersionType());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> findAllByDocumentId(Long documentId) {
        return documentVersionRepository.findByDocument_IdOrderByVersionNumberAsc(documentId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<DocumentVersionResponse> findById(Long id) {
        return documentVersionRepository.findById(id)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentVersionResponse> findCurrentVersion(Long documentId) {
        return documentVersionRepository.findFirstByDocument_IdOrderByVersionNumberDesc(documentId)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentVersionResponse> findByVersionNumber(Long documentId, Integer versionNumber) {
        return documentVersionRepository.findByDocument_IdAndVersionNumber(documentId, versionNumber)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentVersionResponse> findFinalVersion(Long documentId) {
        return documentVersionRepository.findByDocument_IdAndIsFinalTrue(documentId)
                .map(this::toResponse);
    }

    @Transactional
    public DocumentVersionResponse setAsCurrentVersion(Long documentId, Long versionId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + documentId));

        DocumentVersion version = documentVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("버전을 찾을 수 없습니다: " + versionId));

        if (!version.getDocument().getId().equals(documentId)) {
            throw new IllegalArgumentException("버전이 해당 문서에 속하지 않습니다.");
        }

        document.setCurrentVersionId(versionId);
        documentRepository.save(document);

        log.info("현재 버전 설정: 문서 ID {}, 버전 ID {}", documentId, versionId);
        return toResponse(version);
    }

    /**
     * 다음 버전 번호 계산
     * - ORIGINAL: 0
     * - AI_DRAFT: 1
     * - MANUAL_TRANSLATION: 최신 버전 + 1
     * - FINAL: 최신 버전 번호 유지
     */
    private Integer calculateNextVersionNumber(Long documentId, String versionType) {
        switch (versionType) {
            case "ORIGINAL":
                return 0;
            case "AI_DRAFT":
                return 1;
            case "MANUAL_TRANSLATION":
                Optional<DocumentVersion> latest = documentVersionRepository
                        .findFirstByDocument_IdOrderByVersionNumberDesc(documentId);
                return latest.map(v -> v.getVersionNumber() + 1).orElse(2);
            case "FINAL":
                // FINAL은 기존 버전을 최종으로 표시하는 것이므로 최신 버전 번호 사용
                Optional<DocumentVersion> latestForFinal = documentVersionRepository
                        .findFirstByDocument_IdOrderByVersionNumberDesc(documentId);
                return latestForFinal.map(DocumentVersion::getVersionNumber)
                        .orElseThrow(() -> new IllegalArgumentException("FINAL 버전을 생성하기 전에 다른 버전이 필요합니다."));
            default:
                throw new IllegalArgumentException("지원하지 않는 버전 타입입니다: " + versionType);
        }
    }

    private DocumentVersionResponse toResponse(DocumentVersion version) {
        DocumentVersionResponse.DocumentVersionResponseBuilder builder = DocumentVersionResponse.builder()
                .id(version.getId())
                .documentId(version.getDocument().getId())
                .versionNumber(version.getVersionNumber())
                .versionType(version.getVersionType())
                .content(version.getContent())
                .isFinal(version.getIsFinal())
                .createdAt(version.getCreatedAt());

        if (version.getCreatedBy() != null) {
            builder.createdBy(DocumentVersionResponse.CreatorInfo.builder()
                    .id(version.getCreatedBy().getId())
                    .email(version.getCreatedBy().getEmail())
                    .name(version.getCreatedBy().getName())
                    .build());
        }

        return builder.build();
    }
}

