package com.project.Transflow.document.service;

import com.project.Transflow.document.dto.DashboardDocumentCardDto;
import com.project.Transflow.document.dto.DashboardSummaryResponse;
import com.project.Transflow.document.dto.CreateDocumentRequest;
import com.project.Transflow.document.dto.DocumentResponse;
import com.project.Transflow.document.dto.SourceCopySummaryDto;
import com.project.Transflow.document.dto.SourceListEnrichmentRequest;
import com.project.Transflow.document.dto.SourceListEnrichmentResponse;
import com.project.Transflow.document.dto.UpdateDocumentRequest;
import com.project.Transflow.document.entity.Document;
import com.project.Transflow.document.util.ParagraphCounter;
import com.project.Transflow.document.entity.DocumentVersion;
import com.project.Transflow.document.repository.DocumentRepository;
import com.project.Transflow.document.repository.DocumentVersionRepository;
import com.project.Transflow.document.service.HandoverHistoryService;
import com.project.Transflow.document.entity.HandoverHistory;
import com.project.Transflow.task.entity.TranslationTask;
import com.project.Transflow.task.repository.TranslationTaskRepository;
import com.project.Transflow.document.repository.DocumentFavoriteRepository;
import com.project.Transflow.document.repository.HandoverHistoryRepository;
import com.project.Transflow.review.repository.ReviewRepository;
import com.project.Transflow.review.entity.Review;
import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    /** 관리자 번역 세션 하트비트 TTL (분). 이 시간 동안 갱신 없으면 비활성으로 간주 */
    public static final int ADMIN_SESSION_TTL_MINUTES = 5;

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final UserRepository userRepository;
    private final HandoverHistoryService handoverHistoryService;
    private final TranslationTaskRepository translationTaskRepository;
    private final DocumentFavoriteRepository documentFavoriteRepository;
    private final HandoverHistoryRepository handoverHistoryRepository;
    private final ReviewRepository reviewRepository;
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

        // status가 없으면 기본값 DRAFT 사용
        String status = (request.getStatus() != null && !request.getStatus().isEmpty()) 
                ? request.getStatus() 
                : "DRAFT";
        
        // 항상 새 문서 생성 (같은 URL이라도 덮어쓰지 않음)
        Document document = Document.builder()
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
        
        document = documentRepository.save(document);
        log.info("문서 생성: {} (id: {}, 상태: {})", document.getTitle(), document.getId(), status);
        
        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentResponse> findById(Long id) {
        return documentRepository.findById(id)
                .map(this::toResponse);
    }

    /**
     * URL로 이미 문서가 존재하는지 확인 (초벌 번역 중복 방지용).
     * @param url 검사할 URL (trim 적용 권장)
     * @return 존재 여부 및 문서 개수
     */
    @Transactional(readOnly = true)
    public boolean existsByOriginalUrl(String url) {
        if (url == null || url.isBlank()) return false;
        List<Document> docs = documentRepository.findByOriginalUrl(url.trim());
        return !docs.isEmpty();
    }

    /**
     * 현재 사용자가 해당 원문의 복사본을 이미 보유했는지 조회.
     * @param sourceDocumentId 원문 문서 ID
     * @param userId 현재 사용자 ID
     * @return 해당 사용자의 복사본이 있으면 Optional에 담아 반환, 없으면 empty
     */
    @Transactional(readOnly = true)
    public Optional<DocumentResponse> findMyCopyBySourceId(Long sourceDocumentId, Long userId) {
        if (sourceDocumentId == null || userId == null) return Optional.empty();
        return documentRepository.findBySourceDocument_IdAndCreatedBy_Id(sourceDocumentId, userId)
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
        List<Document> docs = documentRepository.findByStatusWithUsers(status);
        List<Long> ids = docs.stream().map(Document::getId).collect(Collectors.toList());
        Map<Long, Long> versionCounts = fetchVersionCountsByDocumentIds(ids);
        return docs.stream()
                .map(doc -> toListResponse(doc, versionCounts.getOrDefault(doc.getId(), 0L)))
                .collect(Collectors.toList());
    }

    /** 내가 작업 중인 복사본 목록 (경량) */
    @Transactional(readOnly = true)
    public List<DocumentResponse> findMyWorkingAssignments(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<Document> docs = documentRepository.findMyWorkingAssignments(userId);
        List<Long> ids = docs.stream().map(Document::getId).collect(Collectors.toList());
        Map<Long, Long> versionCounts = fetchVersionCountsByDocumentIds(ids);
        return docs.stream()
                .map(doc -> toListResponse(doc, versionCounts.getOrDefault(doc.getId(), 0L)))
                .collect(Collectors.toList());
    }

    /** id 목록으로 경량 문서 조회 (원문 배치 로드용) */
    @Transactional(readOnly = true)
    public List<DocumentResponse> findByIdsForList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = ids.stream().distinct().collect(Collectors.toList());
        List<Document> docs = documentRepository.findByIdsWithUsers(distinct);
        Map<Long, Long> versionCounts = fetchVersionCountsByDocumentIds(distinct);
        return docs.stream()
                .map(doc -> toListResponse(doc, versionCounts.getOrDefault(doc.getId(), 0L)))
                .collect(Collectors.toList());
    }

    /**
     * 대시보드용 단일 응답 — 상태별 전체 목록·개별 문서 조회 대신 카드에 필요한 필드만 반환.
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary(Long userId, boolean isAdmin) {
        DashboardSummaryResponse response = DashboardSummaryResponse.builder().build();

        List<Document> pending = documentRepository.findTopByStatusWithUsers(
                "PENDING_TRANSLATION", PageRequest.of(0, 3));
        response.setPendingDocuments(
                pending.stream().map(this::toDashboardCard).collect(Collectors.toList()));

        List<DashboardDocumentCardDto> working = new ArrayList<>();
        if (userId != null) {
            working = documentRepository.findInTranslationForUser(userId, PageRequest.of(0, 3)).stream()
                    .map(this::toDashboardCard)
                    .collect(Collectors.toList());
        }
        response.setWorkingDocuments(working);

        if (!isAdmin) {
            return response;
        }

        response.setReviewPendingCount((int) documentRepository.countByStatus("PENDING_REVIEW"));
        List<Document> latestReviewPending = documentRepository.findTopByStatusWithUsers(
                "PENDING_REVIEW", PageRequest.of(0, 1));
        if (!latestReviewPending.isEmpty()) {
            Document latest = latestReviewPending.get(0);
            DashboardDocumentCardDto latestCard = toDashboardCard(latest);
            reviewRepository.findPendingWithVersionAuthorByDocumentId(latest.getId()).stream()
                    .findFirst()
                    .ifPresent(review -> {
                        if (review.getDocumentVersion() != null
                                && review.getDocumentVersion().getCreatedBy() != null) {
                            latestCard.setTranslatorName(review.getDocumentVersion().getCreatedBy().getName());
                        }
                    });
            response.setLatestReviewDocument(latestCard);
        }

        Map<Long, DashboardDocumentCardDto> approvedByDocId = new HashMap<>();
        for (Document doc : documentRepository.findByStatusWithUsers("APPROVED")) {
            approvedByDocId.putIfAbsent(doc.getId(), toDashboardCard(doc));
        }
        for (Document doc : documentRepository.findByStatusWithUsers("PUBLISHED")) {
            approvedByDocId.putIfAbsent(doc.getId(), toDashboardCard(doc));
        }
        for (Review review : reviewRepository.findByStatusWithDocumentAndReviewer("APPROVED")) {
            Document doc = review.getDocument();
            if (doc == null) {
                continue;
            }
            DashboardDocumentCardDto card = toDashboardCard(doc);
            card.setApprovedReviewId(review.getId());
            card.setPublishedUrl(firstNonBlank(review.getPublishedUrl(), doc.getPublishedUrl()));
            card.setPublishStatus(review.getPublishStatus());
            card.setPublishError(review.getPublishError());
            card.setDisplayAt(firstNonNull(review.getFinalApprovalAt(), doc.getUpdatedAt()));
            approvedByDocId.put(doc.getId(), card);
        }
        List<DashboardDocumentCardDto> approved = approvedByDocId.values().stream()
                .sorted(byDisplayAtDesc())
                .limit(3)
                .collect(Collectors.toList());
        response.setApprovedDocuments(approved);

        List<Review> rejectedReviews = reviewRepository.findByStatusWithDocumentAndReviewer("REJECTED");
        rejectedReviews.sort(Comparator.comparing(
                (Review r) -> firstNonNull(r.getReviewedAt(), r.getUpdatedAt()),
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        List<DashboardDocumentCardDto> rejected = new ArrayList<>();
        java.util.Set<Long> seenRejected = new HashSet<>();
        for (Review review : rejectedReviews) {
            Document doc = review.getDocument();
            if (doc == null || !seenRejected.add(doc.getId())) {
                continue;
            }
            DashboardDocumentCardDto card = toDashboardCard(doc);
            card.setDisplayAt(review.getReviewedAt());
            rejected.add(card);
            if (rejected.size() >= 3) {
                break;
            }
        }
        response.setRejectedDocuments(rejected);

        return response;
    }

    private Comparator<DashboardDocumentCardDto> byDisplayAtDesc() {
        return Comparator.comparing(
                (DashboardDocumentCardDto c) -> firstNonNull(c.getDisplayAt(), c.getUpdatedAt()),
                Comparator.nullsLast(Comparator.naturalOrder())).reversed();
    }

    private DashboardDocumentCardDto toDashboardCard(Document document) {
        return DashboardDocumentCardDto.builder()
                .id(document.getId())
                .title(document.getTitle())
                .categoryId(document.getCategoryId())
                .estimatedLength(document.getEstimatedLength())
                .updatedAt(document.getUpdatedAt())
                .displayAt(document.getUpdatedAt())
                .documentStatus(document.getStatus())
                .publishedUrl(document.getPublishedUrl())
                .build();
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
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
        List<Document> copies = documentRepository.findCopiesBySourceIdWithUsers(sourceDocumentId);
        List<Long> ids = copies.stream().map(Document::getId).collect(Collectors.toList());
        Map<Long, Long> versionCounts = fetchVersionCountsByDocumentIds(ids);
        return copies.stream()
                .map(doc -> toListResponse(doc, versionCounts.getOrDefault(doc.getId(), 0L)))
                .collect(Collectors.toList());
    }

    /**
     * 원문 ID 목록에 대해 IN_TRANSLATION 상태 복사본 개수만 배치 조회 (목록 인원 칸용).
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> countInTranslationCopiesBySourceIds(List<Long> sourceIds) {
        Map<Long, Long> result = new HashMap<>();
        if (sourceIds == null || sourceIds.isEmpty()) {
            return result;
        }
        List<Long> distinct = sourceIds.stream().distinct().collect(Collectors.toList());
        for (Long id : distinct) {
            result.put(id, 0L);
        }
        List<Object[]> rows = documentRepository.countInTranslationCopiesGroupedBySourceId(distinct);
        for (Object[] row : rows) {
            Long sourceId = (Long) row[0];
            long cnt = ((Number) row[1]).longValue();
            result.put(sourceId, cnt);
        }
        return result;
    }

    /**
     * 목록 페이지용 배치 메타: 복사본 요약, 내 IN_TRANSLATION 원문, 진행률 분모(문단 수).
     */
    @Transactional(readOnly = true)
    public SourceListEnrichmentResponse buildSourceListEnrichment(SourceListEnrichmentRequest request, Long userId) {
        SourceListEnrichmentResponse response = SourceListEnrichmentResponse.builder().build();
        if (request == null || request.getSourceDocumentIds() == null || request.getSourceDocumentIds().isEmpty()) {
            return response;
        }

        List<Long> sourceIds = request.getSourceDocumentIds().stream().distinct().collect(Collectors.toList());

        Map<Long, SourceCopySummaryDto> summaries = new HashMap<>();
        for (Long id : sourceIds) {
            summaries.put(id, SourceCopySummaryDto.builder()
                    .totalCopyCount(0)
                    .inTranslationCount(0)
                    .workerNames(new ArrayList<>())
                    .copyStatuses(new ArrayList<>())
                    .build());
        }

        List<Object[]> copyRows = documentRepository.findCopyLightMetaBySourceIds(sourceIds);
        Map<Long, LinkedHashSet<String>> workerSets = new HashMap<>();
        for (Object[] row : copyRows) {
            Long sourceId = (Long) row[0];
            String status = row[1] != null ? row[1].toString() : "";
            String workerName = row[2] != null ? row[2].toString().trim() : "";

            SourceCopySummaryDto summary = summaries.get(sourceId);
            if (summary == null) {
                continue;
            }
            summary.setTotalCopyCount(summary.getTotalCopyCount() + 1);
            if ("IN_TRANSLATION".equals(status)) {
                summary.setInTranslationCount(summary.getInTranslationCount() + 1);
            }
            summary.getCopyStatuses().add(status);
            if (!workerName.isEmpty()) {
                workerSets.computeIfAbsent(sourceId, k -> new LinkedHashSet<>()).add(workerName);
            }
        }
        for (Map.Entry<Long, SourceCopySummaryDto> entry : summaries.entrySet()) {
            LinkedHashSet<String> workers = workerSets.get(entry.getKey());
            if (workers != null) {
                entry.getValue().setWorkerNames(new ArrayList<>(workers));
            }
        }
        response.setCopySummaries(summaries);

        if (userId != null) {
            List<Long> myActive = documentRepository.findSourceIdsWhereUserHasInTranslationCopy(sourceIds, userId);
            response.setMyInTranslationSourceIds(new HashSet<>(myActive));
        }

        List<Long> progressIds = request.getProgressDocumentIds();
        if (progressIds != null && !progressIds.isEmpty()) {
            List<Long> distinctProgress = progressIds.stream().distinct().collect(Collectors.toList());
            Map<Long, Integer> paragraphCounts = new HashMap<>();
            List<Object[]> contentRows = documentVersionRepository.findOriginalContentByDocumentIds(distinctProgress);
            for (Object[] row : contentRows) {
                Long docId = (Long) row[0];
                String content = row[1] != null ? row[1].toString() : "";
                paragraphCounts.put(docId, ParagraphCounter.countParagraphs(content));
            }
            response.setOriginalParagraphCounts(paragraphCounts);
        }

        return response;
    }

    /** 원문만 조회 (복사본 제외). URL 기준 중복 제거 없이 원문을 항상 노출할 때 사용. */
    @Transactional(readOnly = true)
    public List<DocumentResponse> findSourceDocumentsOnly() {
        List<Document> documents = documentRepository.findSourceDocumentsWithUsers();
        if (documents.isEmpty()) {
            return List.of();
        }
        List<Long> ids = documents.stream().map(Document::getId).collect(Collectors.toList());
        Map<Long, Long> versionCounts = fetchVersionCountsByDocumentIds(ids);
        return documents.stream()
                .map(doc -> toListResponse(doc, versionCounts.getOrDefault(doc.getId(), 0L)))
                .collect(Collectors.toList());
    }

    private Map<Long, Long> fetchVersionCountsByDocumentIds(List<Long> documentIds) {
        Map<Long, Long> counts = new HashMap<>();
        for (Long id : documentIds) {
            counts.put(id, 0L);
        }
        for (Object[] row : documentVersionRepository.countVersionsGroupedByDocumentId(documentIds)) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    /** 목록 API용 경량 응답 (handover/review/admin 세션 조회 생략) */
    private DocumentResponse toListResponse(Document document, long versionCount) {
        boolean hasVersions = versionCount > 0;

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

        Long sourceDocumentId = document.getSourceDocument() != null
                ? document.getSourceDocument().getId()
                : null;

        DocumentResponse.DocumentResponseBuilder builder = DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .originalUrl(document.getOriginalUrl())
                .sourceLang(document.getSourceLang())
                .targetLang(document.getTargetLang())
                .categoryId(document.getCategoryId())
                .status(document.getStatus())
                .currentVersionId(document.getCurrentVersionId())
                .estimatedLength(document.getEstimatedLength())
                .versionCount(versionCount)
                .hasVersions(hasVersions)
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
        return builder.build();
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
        if (sourceVersions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "원문에 ORIGINAL 또는 AI_DRAFT 버전이 없어 번역 복사본을 만들 수 없습니다.");
        }
        for (DocumentVersion sv : sourceVersions) {
            DocumentVersion cv = DocumentVersion.builder()
                    .document(copy)
                    .versionNumber(sv.getVersionNumber())
                    .versionType(sv.getVersionType())
                    .content(sv.getContent())
                    .isFinal(sv.getIsFinal())
                    .createdBy(user)
                    .build();
            documentVersionRepository.save(cv);
        }

        // 사람 번역 레이어(v2): 초벌(v1) 기준으로 복사본을 열면 현재 버전은 수동 번역부터 (저장 전에도 v2로 표시)
        String manualBaseContent = "";
        for (DocumentVersion sv : sourceVersions) {
            if ("AI_DRAFT".equals(sv.getVersionType()) && sv.getContent() != null) {
                manualBaseContent = sv.getContent();
                break;
            }
        }
        if (manualBaseContent.isEmpty()) {
            for (DocumentVersion sv : sourceVersions) {
                if ("ORIGINAL".equals(sv.getVersionType()) && sv.getContent() != null) {
                    manualBaseContent = sv.getContent();
                    break;
                }
            }
        }
        // 원문·기존 복사본 전체의 최대 버전 다음 번호 (다른 곳에 v2가 있으면 새 복사본 첫 수동은 v3)
        int nextManualVersionNumber = nextVersionNumberAfterFamilyMax(sourceDocumentId);
        DocumentVersion initialManual = DocumentVersion.builder()
                .document(copy)
                .versionNumber(nextManualVersionNumber)
                .versionType("MANUAL_TRANSLATION")
                .content(manualBaseContent != null ? manualBaseContent : "")
                .isFinal(false)
                .createdBy(user)
                .build();
        DocumentVersion savedManual = documentVersionRepository.save(initialManual);
        copy.setCurrentVersionId(savedManual.getId());
        documentRepository.save(copy);

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
            int nextVersionNum = nextVersionNumberAfterFamilyMax(sourceId);
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

    /**
     * 동일 원문(source) 계열(원문 + 모든 복사본)에서 최대 version_number + 1.
     */
    private int nextVersionNumberAfterFamilyMax(Long sourceId) {
        int max = documentVersionRepository.findMaxVersionNumberInSourceFamily(sourceId).orElse(-1);
        return max + 1;
    }

    @Transactional
    public void deleteDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + id));

        // 1. 원문인 경우: 이 원문을 sourceDocument로 참조하는 모든 복사본을 먼저 삭제
        if (document.getSourceDocument() == null) {
            List<Document> copies = documentRepository.findBySourceDocument_IdOrderByCreatedAtDesc(id);
            for (Document copy : copies) {
                deleteSingleDocumentWithRelations(copy.getId());
            }
        }

        // 2. 현재 문서 및 관련 엔티티 삭제
        deleteSingleDocumentWithRelations(id);
        log.info("문서 및 관련 데이터 삭제 완료: {} (id: {})", document.getTitle(), id);
    }

    /**
     * 단일 문서에 매달린 관련 데이터(작업, 즐겨찾기, 인계, 버전)를 정리하고 문서 자체를 삭제한다.
     */
    @Transactional
    protected void deleteSingleDocumentWithRelations(Long documentId) {
        // 번역 작업 삭제
        List<TranslationTask> tasks = translationTaskRepository.findByDocument_Id(documentId);
        if (!tasks.isEmpty()) {
            translationTaskRepository.deleteAll(tasks);
            log.info("번역 작업 삭제: documentId={}, count={}", documentId, tasks.size());
        }

        // 즐겨찾기 삭제
        documentFavoriteRepository.deleteByDocument_Id(documentId);

        // 인계 히스토리 삭제
        handoverHistoryRepository.deleteByDocument_Id(documentId);

        // 리뷰 삭제 (document_version FK 때문에 버전보다 먼저 제거)
        reviewRepository.deleteByDocument_Id(documentId);

        // 버전 삭제 + currentVersionId 초기화
        List<DocumentVersion> versions = documentVersionRepository.findByDocument_Id(documentId);
        if (!versions.isEmpty()) {
            documentVersionRepository.deleteAll(versions);
            log.info("문서 버전 삭제: documentId={}, count={}", documentId, versions.size());
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + documentId));
        document.setCurrentVersionId(null);
        documentRepository.delete(document);
    }

    private DocumentResponse toResponse(Document document) {
        // 버전 개수 조회
        long versionCount = documentVersionRepository.countByDocument_Id(document.getId());
        boolean hasVersions = versionCount > 0;
        
        Integer currentVersionNumber = null;
        Boolean currentVersionIsFinal = null;
        Integer userFacingVersionNumber = null;
        if (document.getCurrentVersionId() != null) {
            var currentVersionOpt = documentVersionRepository.findById(document.getCurrentVersionId());
            if (currentVersionOpt.isPresent()) {
                DocumentVersion cv = currentVersionOpt.get();
                currentVersionNumber = cv.getVersionNumber();
                currentVersionIsFinal = Boolean.TRUE.equals(cv.getIsFinal());
                if ("ORIGINAL".equals(cv.getVersionType())) {
                    userFacingVersionNumber = 1;
                } else {
                    userFacingVersionNumber = cv.getVersionNumber();
                }
            }
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
                .userFacingVersionNumber(userFacingVersionNumber)
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

        enrichPublishInfo(document, builder);
        enrichAdminSession(document, builder);

        return builder.build();
    }

    private void enrichPublishInfo(Document document, DocumentResponse.DocumentResponseBuilder builder) {
        builder.publishedUrl(document.getPublishedUrl());
        reviewRepository.findTopByDocument_IdAndStatusOrderByFinalApprovalAtDesc(document.getId(), "APPROVED")
                .ifPresent(review -> {
                    builder.approvedReviewId(review.getId());
                    builder.publishStatus(review.getPublishStatus());
                    builder.publishError(review.getPublishError());
                    builder.publishedAt(review.getPublishedAt());
                    if (review.getPublishedUrl() != null && !review.getPublishedUrl().isBlank()) {
                        builder.publishedUrl(review.getPublishedUrl());
                    }
                });
    }

    /**
     * 원문(또는 복사본) 기준 루트 원문 문서를 반환합니다.
     */
    public Document getSourceRoot(Document document) {
        if (document.getSourceDocument() == null) {
            return document;
        }
        Long sid = document.getSourceDocument().getId();
        return documentRepository.findById(sid).orElse(document);
    }

    public boolean isAdminTranslationSessionActive(Document source) {
        if (source == null
                || source.getAdminSessionCopyDocumentId() == null
                || source.getAdminSessionAt() == null) {
            return false;
        }
        return source.getAdminSessionAt().isAfter(LocalDateTime.now().minusMinutes(ADMIN_SESSION_TTL_MINUTES));
    }

    private void enrichAdminSession(Document document, DocumentResponse.DocumentResponseBuilder builder) {
        Document source = getSourceRoot(document);
        if (!isAdminTranslationSessionActive(source)) {
            builder.adminTranslationSessionActive(false)
                    .adminSessionCopyDocumentId(null)
                    .adminSessionUser(null);
            return;
        }
        builder.adminTranslationSessionActive(true)
                .adminSessionCopyDocumentId(source.getAdminSessionCopyDocumentId());
        Long uid = source.getAdminSessionUserId();
        if (uid != null) {
            userRepository.findById(uid).ifPresent(u -> builder.adminSessionUser(DocumentResponse.CreatorInfo.builder()
                    .id(u.getId())
                    .email(u.getEmail())
                    .name(u.getName())
                    .build()));
        }
    }

    /**
     * 번역 임시저장/완료/인계 편집 권한:
     * - 관리자 세션이 없으면 누구나 허용
     * - 관리자 세션이 있으면 세션을 연 사용자만 허용 (관리자/봉사자 모두 동일 규칙)
     */
    public void assertVolunteerCanEditTranslation(Long documentId, Long userId, Integer roleLevel) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));
        Document source = getSourceRoot(doc);
        if (!isAdminTranslationSessionActive(source)) {
            return;
        }
        Long sessionOwnerId = source.getAdminSessionUserId();
        if (sessionOwnerId != null && sessionOwnerId.equals(userId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "중앙/최고 관리자가 이 원문 번역을 진행 중입니다. 세션 소유자만 편집할 수 있습니다.");
    }

    @Transactional
    public void startAdminTranslationSession(Long copyDocumentId, Long adminUserId) {
        Document copy = documentRepository.findById(copyDocumentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));
        if (copy.getSourceDocument() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "원문 문서에는 관리자 번역 세션을 연결할 수 없습니다. 복사본 문서에서 시작하세요.");
        }
        Document source = getSourceRoot(copy);
        source.setAdminSessionCopyDocumentId(copy.getId());
        source.setAdminSessionUserId(adminUserId);
        source.setAdminSessionAt(LocalDateTime.now());
        documentRepository.save(source);
        log.info("관리자 번역 세션 시작: sourceId={}, copyId={}, adminUserId={}", source.getId(), copy.getId(), adminUserId);
    }

    @Transactional
    public void heartbeatAdminTranslationSession(Long copyDocumentId, Long adminUserId) {
        Document copy = documentRepository.findById(copyDocumentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));
        if (copy.getSourceDocument() == null) {
            return;
        }
        Document source = getSourceRoot(copy);
        if (!adminUserId.equals(source.getAdminSessionUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 세션을 연 관리자가 아닙니다.");
        }
        if (!copy.getId().equals(source.getAdminSessionCopyDocumentId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "원문에 다른 복사본이 관리자 세션으로 등록되어 있습니다.");
        }
        source.setAdminSessionAt(LocalDateTime.now());
        documentRepository.save(source);
    }

    @Transactional
    public void endAdminTranslationSession(Long copyDocumentId, Long adminUserId) {
        Document copy = documentRepository.findById(copyDocumentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));
        if (copy.getSourceDocument() == null) {
            return;
        }
        Document source = getSourceRoot(copy);
        if (!adminUserId.equals(source.getAdminSessionUserId())) {
            return;
        }
        source.setAdminSessionCopyDocumentId(null);
        source.setAdminSessionUserId(null);
        source.setAdminSessionAt(null);
        documentRepository.save(source);
        log.info("관리자 번역 세션 종료: copyId={}, adminUserId={}", copyDocumentId, adminUserId);
    }

    /**
     * 번역 완료 등으로 관리자 세션을 해제합니다(해당 복사본이 세션 대상일 때만).
     */
    @Transactional
    public void clearAdminTranslationSessionIfEditingCopy(Long documentId) {
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null || doc.getSourceDocument() == null) {
            return;
        }
        Document source = getSourceRoot(doc);
        if (doc.getId().equals(source.getAdminSessionCopyDocumentId())) {
            source.setAdminSessionCopyDocumentId(null);
            source.setAdminSessionUserId(null);
            source.setAdminSessionAt(null);
            documentRepository.save(source);
            log.info("번역 완료로 관리자 세션 해제: copyId={}", documentId);
        }
    }
}

