package com.project.Transflow.document.service;

import com.project.Transflow.document.dto.CreateDocumentRequest;
import com.project.Transflow.document.dto.DocumentResponse;
import com.project.Transflow.document.dto.UpdateDocumentRequest;
import com.project.Transflow.document.entity.Document;
import com.project.Transflow.document.entity.DocumentVersion;
import com.project.Transflow.document.repository.DocumentRepository;
import com.project.Transflow.document.repository.DocumentVersionRepository;
import com.project.Transflow.document.service.HandoverHistoryService;
import com.project.Transflow.document.entity.HandoverHistory;
import com.project.Transflow.task.entity.TranslationTask;
import com.project.Transflow.task.repository.TranslationTaskRepository;
import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final UserRepository userRepository;
    private final HandoverHistoryService handoverHistoryService;
    private final TranslationTaskRepository translationTaskRepository;
    private final ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Transactional
    public DocumentResponse createDocument(CreateDocumentRequest request, Long createdById) {
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

        // 같은 URL의 문서가 있는지 확인 (DRAFT 또는 PENDING_TRANSLATION 상태인 경우)
        List<Document> existingDocs = documentRepository.findByOriginalUrlOrderByCreatedAtDesc(request.getOriginalUrl());
        Optional<Document> existingDoc = existingDocs.stream()
                .filter(doc -> "DRAFT".equals(doc.getStatus()) || "PENDING_TRANSLATION".equals(doc.getStatus()))
                .findFirst();

        // status가 없으면 기본값 DRAFT 사용
        String status = (request.getStatus() != null && !request.getStatus().isEmpty()) 
                ? request.getStatus() 
                : "DRAFT";
        
        Document document;
        if (existingDoc.isPresent() && ("DRAFT".equals(status) || "PENDING_TRANSLATION".equals(status))) {
            // 같은 URL의 DRAFT 또는 PENDING_TRANSLATION 문서가 있으면 제목 업데이트
            Document docToUpdate = existingDoc.get();
            String oldStatus = docToUpdate.getStatus();
            docToUpdate.setTitle(request.getTitle());
            docToUpdate.setStatus(status); // 상태도 업데이트 (Step 6에서 선택한 상태로)
            if (request.getCategoryId() != null) {
                docToUpdate.setCategoryId(request.getCategoryId());
            }
            if (request.getEstimatedLength() != null) {
                docToUpdate.setEstimatedLength(request.getEstimatedLength());
            }
            if (request.getDraftData() != null) {
                docToUpdate.setDraftData(request.getDraftData());
            }
            docToUpdate.setLastModifiedBy(createdBy);
            document = documentRepository.save(docToUpdate);
            log.info("기존 문서 제목 업데이트: {} (id: {}, 상태: {} -> {})", 
                    document.getTitle(), document.getId(), oldStatus, status);
        } else {
            // 새 문서 생성
            document = Document.builder()
                    .title(request.getTitle())
                    .originalUrl(request.getOriginalUrl())
                    .sourceLang(request.getSourceLang())
                    .targetLang(request.getTargetLang())
                    .categoryId(request.getCategoryId())
                    .status(status)
                    .estimatedLength(request.getEstimatedLength())
                    .draftData(request.getDraftData())
                    .createdBy(createdBy)
                    .build();
            
            log.info("문서 생성 - 상태: {}", status);
            document = documentRepository.save(document);
            log.info("문서 생성: {} (id: {})", document.getTitle(), document.getId());
        }
        
        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentResponse> findById(Long id) {
        return documentRepository.findById(id)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findAll() {
        List<Document> allDocs = documentRepository.findAll();
        // 같은 URL의 문서 중 최신 버전만 선택
        Map<String, Document> latestDocsByUrl = new java.util.HashMap<>();
        for (Document doc : allDocs) {
            String url = doc.getOriginalUrl();
            Document existing = latestDocsByUrl.get(url);
            if (existing == null || doc.getUpdatedAt().isAfter(existing.getUpdatedAt())) {
                latestDocsByUrl.put(url, doc);
            }
        }
        return latestDocsByUrl.values().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findAllExcludingPendingTranslation() {
        // PENDING_TRANSLATION 제외는 더 이상 필요 없지만, 호환성을 위해 유지
        // 같은 URL의 문서 중 최신 버전만 선택
        List<Document> allDocs = documentRepository.findAll().stream()
                .filter(doc -> !"PENDING_TRANSLATION".equals(doc.getStatus()))
                .collect(Collectors.toList());
        Map<String, Document> latestDocsByUrl = new java.util.HashMap<>();
        for (Document doc : allDocs) {
            String url = doc.getOriginalUrl();
            Document existing = latestDocsByUrl.get(url);
            if (existing == null || doc.getUpdatedAt().isAfter(existing.getUpdatedAt())) {
                latestDocsByUrl.put(url, doc);
            }
        }
        return latestDocsByUrl.values().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findByStatus(String status) {
        return documentRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 번역 대기 중인 원문만 조회. 이미 해당 원문에 대해 APPROVED/PUBLISHED 문서가 있으면 제외(종료된 원문 제외).
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> findPendingTranslationSourcesNotFinalized() {
        return documentRepository.findPendingTranslationSourcesNotFinalized().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** 원문 ID로 해당 원문의 복사본(다른 사람 작업물) 목록 조회 */
    @Transactional(readOnly = true)
    public List<DocumentResponse> findCopiesBySourceDocumentId(Long sourceDocumentId) {
        return documentRepository.findBySourceDocument_IdOrderByCreatedAtDesc(sourceDocumentId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** 원문만 조회 (복사본 제외). URL 기준 중복 제거 없이 원문을 항상 노출할 때 사용. */
    @Transactional(readOnly = true)
    public List<DocumentResponse> findSourceDocumentsOnly() {
        return documentRepository.findBySourceDocumentIsNull().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findByCategoryId(Long categoryId) {
        return documentRepository.findByCategoryId(categoryId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findByCreatedBy(Long createdById) {
        return documentRepository.findByCreatedBy_Id(createdById).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findByTitleContaining(String title) {
        return documentRepository.findByTitleContainingIgnoreCase(title).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public DocumentResponse updateDocument(Long id, UpdateDocumentRequest request, Long modifiedById) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + id));

        User lastModifiedBy = userRepository.findById(modifiedById)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + modifiedById));

        if (request.getTitle() != null) {
            document.setTitle(request.getTitle());
        }
        if (request.getOriginalUrl() != null) {
            document.setOriginalUrl(request.getOriginalUrl());
        }
        if (request.getSourceLang() != null) {
            document.setSourceLang(request.getSourceLang());
        }
        if (request.getTargetLang() != null) {
            document.setTargetLang(request.getTargetLang());
        }
        if (request.getCategoryId() != null) {
            document.setCategoryId(request.getCategoryId());
        }
        if (request.getStatus() != null) {
            document.setStatus(request.getStatus());
        }
        if (request.getEstimatedLength() != null) {
            document.setEstimatedLength(request.getEstimatedLength());
        }
        if (request.getDraftData() != null) {
            document.setDraftData(request.getDraftData());
        }
        if (request.getCompletedParagraphs() != null) {
            try {
                document.setCompletedParagraphs(objectMapper.writeValueAsString(request.getCompletedParagraphs()));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("completedParagraphs JSON 직렬화 실패: documentId={}", id, e);
            }
        }

        document.setLastModifiedBy(lastModifiedBy);

        Document saved = documentRepository.save(document);
        log.info("문서 수정: {} (id: {})", saved.getTitle(), saved.getId());
        return toResponse(saved);
    }

    /**
     * 봉사자: 원문 문서에서 번역용 복사본을 생성하고 작업을 시작합니다.
     * 복사본에는 원문의 ORIGINAL, AI_DRAFT 버전이 복사되며, 새 문서는 IN_TRANSLATION 상태로 생성됩니다.
     */
    @Transactional
    public DocumentResponse createCopyForTranslation(Long sourceDocumentId, Long userId) {
        Document source = documentRepository.findById(sourceDocumentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "원문 문서를 찾을 수 없습니다."));
        if (source.getSourceDocument() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "원문 문서만 번역 시작할 수 있습니다. 복사본에서 시작하려면 관리자용 '이어서 작업'을 사용하세요.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용자를 찾을 수 없습니다."));

        Document copy = Document.builder()
                .title(source.getTitle())
                .originalUrl(source.getOriginalUrl())
                .sourceLang(source.getSourceLang())
                .targetLang(source.getTargetLang())
                .categoryId(source.getCategoryId())
                .status("IN_TRANSLATION")
                .estimatedLength(source.getEstimatedLength())
                .sourceDocument(source)
                .createdBy(user)
                .build();
        copy = documentRepository.save(copy);

        List<DocumentVersion> sourceVersions = documentVersionRepository
                .findByDocument_IdOrderByVersionNumberAsc(sourceDocumentId).stream()
                .filter(v -> "ORIGINAL".equals(v.getVersionType()) || "AI_DRAFT".equals(v.getVersionType()))
                .collect(Collectors.toList());
        DocumentVersion lastCopied = null;
        for (DocumentVersion sv : sourceVersions) {
            DocumentVersion cv = DocumentVersion.builder()
                    .document(copy)
                    .versionNumber(sv.getVersionNumber())
                    .versionType(sv.getVersionType())
                    .content(sv.getContent())
                    .isFinal(sv.getIsFinal())
                    .createdBy(user)
                    .build();
            lastCopied = documentVersionRepository.save(cv);
        }
        if (lastCopied != null) {
            copy.setCurrentVersionId(lastCopied.getId());
            documentRepository.save(copy);
        }

        TranslationTask task = TranslationTask.builder()
                .document(copy)
                .translator(user)
                .assignedBy(null)
                .status("IN_PROGRESS")
                .startedAt(LocalDateTime.now())
                .lastActivityAt(LocalDateTime.now())
                .build();
        translationTaskRepository.save(task);
        log.info("번역 복사본 생성: sourceId={}, copyId={}, userId={}", sourceDocumentId, copy.getId(), userId);
        return toResponse(copy);
    }

    /**
     * 관리자: 다른 문서의 작업을 이어받아 새 복사본을 생성합니다.
     * 복사본에는 원문의 ORIGINAL/AI_DRAFT와, 이어받을 문서의 최신 내용이 MANUAL_TRANSLATION으로 복사됩니다.
     */
    @Transactional
    public DocumentResponse createCopyForContinuation(Long fromDocumentId, Long userId) {
        Document fromDoc = documentRepository.findById(fromDocumentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용자를 찾을 수 없습니다."));

        Document source = fromDoc.getSourceDocument() != null ? fromDoc.getSourceDocument() : fromDoc;
        Long sourceId = source.getId();

        Document copy = Document.builder()
                .title(fromDoc.getTitle())
                .originalUrl(fromDoc.getOriginalUrl())
                .sourceLang(fromDoc.getSourceLang())
                .targetLang(fromDoc.getTargetLang())
                .categoryId(fromDoc.getCategoryId())
                .status("IN_TRANSLATION")
                .estimatedLength(fromDoc.getEstimatedLength())
                .sourceDocument(source)
                .createdBy(user)
                .build();
        copy = documentRepository.save(copy);

        List<DocumentVersion> sourceVersions = documentVersionRepository
                .findByDocument_IdOrderByVersionNumberAsc(sourceId).stream()
                .filter(v -> "ORIGINAL".equals(v.getVersionType()) || "AI_DRAFT".equals(v.getVersionType()))
                .collect(Collectors.toList());
        for (DocumentVersion sv : sourceVersions) {
            DocumentVersion cv = DocumentVersion.builder()
                    .document(copy)
                    .versionNumber(sv.getVersionNumber())
                    .versionType(sv.getVersionType())
                    .content(sv.getContent())
                    .isFinal(false)
                    .createdBy(user)
                    .build();
            documentVersionRepository.save(cv);
        }

        Optional<DocumentVersion> fromLatest = documentVersionRepository.findFirstByDocument_IdOrderByVersionNumberDesc(fromDocumentId);
        if (fromLatest.isPresent() && ("MANUAL_TRANSLATION".equals(fromLatest.get().getVersionType()) || "AI_DRAFT".equals(fromLatest.get().getVersionType()))) {
            int nextVersionNum = sourceVersions.isEmpty() ? 2 : sourceVersions.get(sourceVersions.size() - 1).getVersionNumber() + 1;
            DocumentVersion continued = DocumentVersion.builder()
                    .document(copy)
                    .versionNumber(nextVersionNum)
                    .versionType("MANUAL_TRANSLATION")
                    .content(fromLatest.get().getContent())
                    .isFinal(false)
                    .createdBy(user)
                    .build();
            documentVersionRepository.save(continued);
        }

        List<DocumentVersion> copiedVersions = documentVersionRepository.findByDocument_IdOrderByVersionNumberAsc(copy.getId());
        if (!copiedVersions.isEmpty()) {
            copy.setCurrentVersionId(copiedVersions.get(copiedVersions.size() - 1).getId());
            documentRepository.save(copy);
        }

        TranslationTask task = TranslationTask.builder()
                .document(copy)
                .translator(user)
                .assignedBy(null)
                .status("IN_PROGRESS")
                .startedAt(LocalDateTime.now())
                .lastActivityAt(LocalDateTime.now())
                .build();
        translationTaskRepository.save(task);
        log.info("이어받기 복사본 생성: fromDocId={}, copyId={}, userId={}", fromDocumentId, copy.getId(), userId);
        return toResponse(copy);
    }

    @Transactional
    public void deleteDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + id));

        documentRepository.delete(document);
        log.info("문서 삭제: {} (id: {})", document.getTitle(), id);
    }

    private DocumentResponse toResponse(Document document) {
        // 버전 개수 조회
        long versionCount = documentVersionRepository.countByDocument_Id(document.getId());
        boolean hasVersions = versionCount > 0;
        
        Integer currentVersionNumber = null;
        Boolean currentVersionIsFinal = null;
        if (document.getCurrentVersionId() != null) {
            var currentVersionOpt = documentVersionRepository.findById(document.getCurrentVersionId());
            currentVersionNumber = currentVersionOpt.map(DocumentVersion::getVersionNumber).orElse(null);
            currentVersionIsFinal = currentVersionOpt.map(v -> Boolean.TRUE.equals(v.getIsFinal())).orElse(null);
        }

        // sourceDocumentId (원문 참조)
        Long sourceDocumentId = null;
        if (document.getSourceDocument() != null) {
            sourceDocumentId = document.getSourceDocument().getId();
        }

        // completedParagraphs JSON 파싱
        java.util.List<Integer> completedParagraphsList = null;
        if (document.getCompletedParagraphs() != null && !document.getCompletedParagraphs().isEmpty()) {
            try {
                completedParagraphsList = objectMapper.readValue(
                    document.getCompletedParagraphs(),
                    new TypeReference<java.util.List<Integer>>() {}
                );
            } catch (Exception e) {
                log.warn("문서 completedParagraphs JSON 파싱 실패: documentId={}", document.getId(), e);
            }
        }

        DocumentResponse.DocumentResponseBuilder builder = DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .originalUrl(document.getOriginalUrl())
                .sourceLang(document.getSourceLang())
                .targetLang(document.getTargetLang())
                .categoryId(document.getCategoryId())
                .status(document.getStatus())
                .currentVersionId(document.getCurrentVersionId())
                .currentVersionNumber(currentVersionNumber)
                .currentVersionIsFinal(currentVersionIsFinal)
                .estimatedLength(document.getEstimatedLength())
                .versionCount(versionCount)
                .hasVersions(hasVersions)
                .draftData(document.getDraftData())
                .sourceDocumentId(sourceDocumentId)
                .completedParagraphs(completedParagraphsList)
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt());

        if (document.getCreatedBy() != null) {
            builder.createdBy(DocumentResponse.CreatorInfo.builder()
                    .id(document.getCreatedBy().getId())
                    .email(document.getCreatedBy().getEmail())
                    .name(document.getCreatedBy().getName())
                    .build());
        }

        if (document.getLastModifiedBy() != null) {
            builder.lastModifiedBy(DocumentResponse.ModifierInfo.builder()
                    .id(document.getLastModifiedBy().getId())
                    .email(document.getLastModifiedBy().getEmail())
                    .name(document.getLastModifiedBy().getName())
                    .build());
        }

        // 최신 인계 정보 추가
        Optional<HandoverHistory> latestHandover = handoverHistoryService.findLatestByDocumentId(document.getId());
        if (latestHandover.isPresent()) {
            HandoverHistory handover = latestHandover.get();
            
            // 인계 히스토리 completedParagraphs JSON 파싱
            java.util.List<Integer> handoverCompletedParagraphsList = null;
            if (handover.getCompletedParagraphs() != null && !handover.getCompletedParagraphs().isEmpty()) {
                try {
                    handoverCompletedParagraphsList = objectMapper.readValue(
                        handover.getCompletedParagraphs(),
                        new TypeReference<java.util.List<Integer>>() {}
                    );
                } catch (Exception e) {
                    log.warn("인계 히스토리의 completedParagraphs 파싱 실패: {}", e.getMessage());
                }
            }

            DocumentResponse.HandoverInfo.HandoverInfoBuilder handoverBuilder = DocumentResponse.HandoverInfo.builder()
                    .memo(handover.getMemo())
                    .terms(handover.getTerms())
                    .completedParagraphs(handoverCompletedParagraphsList)
                    .handedOverAt(handover.getCreatedAt());

            if (handover.getHandedOverBy() != null) {
                handoverBuilder.handedOverBy(DocumentResponse.CreatorInfo.builder()
                        .id(handover.getHandedOverBy().getId())
                        .email(handover.getHandedOverBy().getEmail())
                        .name(handover.getHandedOverBy().getName())
                        .build());
            }

            builder.latestHandover(handoverBuilder.build());
        }

        return builder.build();
    }
}

