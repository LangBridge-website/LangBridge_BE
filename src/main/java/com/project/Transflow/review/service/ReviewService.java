package com.project.Transflow.review.service;

import com.project.Transflow.document.entity.Document;
import com.project.Transflow.document.entity.DocumentVersion;
import com.project.Transflow.document.repository.DocumentRepository;
import com.project.Transflow.document.repository.DocumentVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.Transflow.publish.dto.PublishResult;
import com.project.Transflow.publish.service.CreationKrBoardCatalogService;
import com.project.Transflow.publish.service.CreationKrPublishHtmlSanitizer;
import com.project.Transflow.publish.service.CreationKrPublishService;
import com.project.Transflow.review.dto.CreateReviewRequest;
import com.project.Transflow.review.dto.PublishPreviewResponse;
import com.project.Transflow.review.dto.PublishReviewRequest;
import com.project.Transflow.review.dto.ReviewResponse;
import com.project.Transflow.review.dto.UpdateReviewRequest;
import com.project.Transflow.review.entity.Review;
import com.project.Transflow.review.repository.ReviewRepository;
import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final UserRepository userRepository;
    private final CreationKrPublishService creationKrPublishService;
    private final CreationKrBoardCatalogService creationKrBoardCatalogService;
    private final CreationKrPublishHtmlSanitizer htmlSanitizer;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request, Long reviewerId) {
        Document document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + request.getDocumentId()));

        DocumentVersion documentVersion = documentVersionRepository.findById(request.getDocumentVersionId())
                .orElseThrow(() -> new IllegalArgumentException("문서 버전을 찾을 수 없습니다: " + request.getDocumentVersionId()));

        // 버전이 해당 문서에 속하는지 확인
        if (!documentVersion.getDocument().getId().equals(request.getDocumentId())) {
            throw new IllegalArgumentException("문서 버전이 해당 문서에 속하지 않습니다.");
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰어를 찾을 수 없습니다: " + reviewerId));

        // 이미 리뷰가 있는지 확인
        Optional<Review> existingReview = reviewRepository
                .findByDocument_IdAndDocumentVersion_Id(request.getDocumentId(), request.getDocumentVersionId());
        if (existingReview.isPresent()) {
            throw new IllegalArgumentException("이미 해당 버전에 대한 리뷰가 존재합니다.");
        }

        // Checklist를 JSON 문자열로 변환
        String checklistJson = null;
        if (request.getChecklist() != null) {
            try {
                checklistJson = objectMapper.writeValueAsString(request.getChecklist());
            } catch (JsonProcessingException e) {
                log.error("체크리스트 JSON 변환 실패", e);
            }
        }

        Review review = Review.builder()
                .document(document)
                .documentVersion(documentVersion)
                .reviewer(reviewer)
                .status("PENDING")
                .comment(request.getComment())
                .checklist(checklistJson)
                .isComplete(request.getIsComplete() != null ? request.getIsComplete() : false)
                .build();

        Review saved = reviewRepository.save(review);
        log.info("리뷰 생성: 문서 ID {}, 버전 ID {}, 리뷰어 ID {}", request.getDocumentId(), request.getDocumentVersionId(), reviewerId);
        return toResponse(saved);
    }

    /**
     * 번역 완료 등으로 문서가 검토 대기 상태가 될 때 PENDING 리뷰를 자동 생성합니다.
     * 동일 문서·버전에 리뷰가 이미 있으면 기존 리뷰를 반환합니다.
     */
    @Transactional
    public Optional<ReviewResponse> ensurePendingReviewForDocument(Long documentId, Long documentVersionId) {
        Optional<Review> existingReview = reviewRepository
                .findByDocument_IdAndDocumentVersion_Id(documentId, documentVersionId);
        if (existingReview.isPresent()) {
            return Optional.of(toResponse(existingReview.get()));
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + documentId));

        DocumentVersion documentVersion = documentVersionRepository.findById(documentVersionId)
                .orElseThrow(() -> new IllegalArgumentException("문서 버전을 찾을 수 없습니다: " + documentVersionId));

        if (!documentVersion.getDocument().getId().equals(documentId)) {
            throw new IllegalArgumentException("문서 버전이 해당 문서에 속하지 않습니다.");
        }

        Review review = Review.builder()
                .document(document)
                .documentVersion(documentVersion)
                .reviewer(null)
                .status("PENDING")
                .isComplete(true)
                .build();

        Review saved = reviewRepository.save(review);
        log.info("검토 대기 리뷰 자동 생성: 문서 ID {}, 버전 ID {}", documentId, documentVersionId);
        return Optional.of(toResponse(saved));
    }

    /**
     * 문서의 최신 MANUAL_TRANSLATION 버전 기준으로 PENDING 리뷰를 자동 생성합니다.
     */
    @Transactional
    public Optional<ReviewResponse> ensurePendingReviewForLatestManualTranslation(Long documentId) {
        Optional<DocumentVersion> latestManualVersion = documentVersionRepository.findByDocument_Id(documentId).stream()
                .filter(version -> "MANUAL_TRANSLATION".equals(version.getVersionType()))
                .max(Comparator.comparing(DocumentVersion::getVersionNumber));

        if (latestManualVersion.isEmpty()) {
            log.warn("MANUAL_TRANSLATION 버전이 없어 리뷰 자동 생성을 건너뜁니다. 문서 ID {}", documentId);
            return Optional.empty();
        }

        return ensurePendingReviewForDocument(documentId, latestManualVersion.get().getId());
    }

    @Transactional
    public ReviewResponse approveReview(Long reviewId, Long reviewerId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다: " + reviewId));

        assignReviewerIfNeeded(review, reviewerId);

        if (!"PENDING".equals(review.getStatus())) {
            throw new IllegalArgumentException("승인할 수 없는 상태입니다. 현재 상태: " + review.getStatus());
        }

        review.setStatus("APPROVED");
        review.setReviewedAt(LocalDateTime.now());
        review.setFinalApprovalAt(LocalDateTime.now());

        // DocumentVersion을 최종 버전으로 설정
        DocumentVersion version = review.getDocumentVersion();
        // 기존 최종 버전 해제
        documentVersionRepository.findByDocument_IdAndIsFinalTrue(review.getDocument().getId())
                .ifPresent(v -> v.setIsFinal(false));
        version.setIsFinal(true);
        documentVersionRepository.save(version);

        // Document 상태 업데이트
        Document document = review.getDocument();
        document.setCurrentVersionId(version.getId());
        
        // isComplete가 false면 부분 번역이므로 다시 번역 대기 상태로 변경
        // isComplete가 true면 완전 번역이므로 APPROVED 상태 유지
        if (review.getIsComplete() != null && !review.getIsComplete()) {
            // 부분 번역: 다른 번역가가 이어서 작업할 수 있도록 PENDING_TRANSLATION으로 변경
            document.setStatus("PENDING_TRANSLATION");
            log.info("부분 번역 승인: 문서 ID {}를 다시 번역 대기 상태로 변경", document.getId());
        } else {
            // 완전 번역: APPROVED 상태로 설정
            document.setStatus("APPROVED");
        }
        documentRepository.save(document);

        Review saved = reviewRepository.save(review);
        log.info("리뷰 승인: 리뷰 ID {}", reviewId);
        return toResponse(saved);
    }

    @Transactional
    public ReviewResponse rejectReview(Long reviewId, Long reviewerId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다: " + reviewId));

        assignReviewerIfNeeded(review, reviewerId);

        if (!"PENDING".equals(review.getStatus())) {
            throw new IllegalArgumentException("반려할 수 없는 상태입니다. 현재 상태: " + review.getStatus());
        }

        review.setStatus("REJECTED");
        review.setReviewedAt(LocalDateTime.now());

        // Document 상태 업데이트 (다시 번역 대기 상태로 변경)
        Document document = review.getDocument();
        document.setStatus("PENDING_TRANSLATION");
        documentRepository.save(document);

        Review saved = reviewRepository.save(review);
        log.info("리뷰 반려: 리뷰 ID {}", reviewId);
        return toResponse(saved);
    }

    @Transactional
    public ReviewResponse publishReview(Long reviewId, Long adminUserId) {
        return publishReview(reviewId, adminUserId, null);
    }

    public ReviewResponse publishReview(Long reviewId, Long adminUserId, PublishReviewRequest request) {
        PublishBoardSelection boardSelection = preparePublishInNewTransaction(reviewId, request);

        PublishResult result;
        try {
            Review review = loadReviewForPublish(reviewId);
            result = creationKrPublishService.publishFromReview(
                    review, boardSelection.sitePath(), boardSelection.boardId());
        } catch (IllegalStateException e) {
            return finalizePublishInNewTransaction(reviewId, PublishResult.failure(e.getMessage()));
        } catch (Exception e) {
            log.error("creation.kr 게시 중 예외 - reviewId: {}", reviewId, e);
            return finalizePublishInNewTransaction(reviewId, PublishResult.failure(e.getMessage()));
        }

        return finalizePublishInNewTransaction(reviewId, result);
    }

    @Transactional(readOnly = true)
    public PublishPreviewResponse getPublishPreview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다: " + reviewId));

        Document document = review.getDocument();
        DocumentVersion version = review.getDocumentVersion();

        String htmlContent = version != null ? version.getContent() : null;
        String sanitizedHtml = htmlSanitizer.sanitize(htmlContent, document.getOriginalUrl());

        String publishStatus = review.getPublishStatus() != null ? review.getPublishStatus() : "NONE";
        boolean publishable = "APPROVED".equals(review.getStatus())
                && Boolean.TRUE.equals(review.getIsComplete())
                && "APPROVED".equals(document.getStatus())
                && !"PENDING".equals(publishStatus)
                && !("SUCCESS".equals(publishStatus) && review.getPublishedUrl() != null && !review.getPublishedUrl().isBlank());

        return PublishPreviewResponse.builder()
                .reviewId(review.getId())
                .documentId(document.getId())
                .title(document.getTitle())
                .sanitizedHtml(sanitizedHtml)
                .originalUrl(document.getOriginalUrl())
                .categoryId(document.getCategoryId())
                .reviewStatus(review.getStatus())
                .documentStatus(document.getStatus())
                .publishStatus(publishStatus)
                .publishedUrl(review.getPublishedUrl())
                .publishError(review.getPublishError())
                .isComplete(review.getIsComplete())
                .publishable(publishable)
                .build();
    }

    private PublishBoardSelection preparePublishInNewTransaction(Long reviewId, PublishReviewRequest request) {
        TransactionTemplate template = newRequiresNewTemplate();
        return template.execute(status -> {
            Review review = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다: " + reviewId));

            if (!"APPROVED".equals(review.getStatus())) {
                throw new IllegalArgumentException("승인된 리뷰만 게시할 수 있습니다. 현재 상태: " + review.getStatus());
            }

            if (Boolean.FALSE.equals(review.getIsComplete())) {
                throw new IllegalArgumentException("완전 번역으로 승인된 문서만 creation.kr에 게시할 수 있습니다.");
            }

            Document document = review.getDocument();
            if (!"APPROVED".equals(document.getStatus())) {
                throw new IllegalArgumentException("문서 상태가 APPROVED가 아닙니다. 현재 상태: " + document.getStatus());
            }

            String currentPublishStatus = review.getPublishStatus() != null ? review.getPublishStatus() : "NONE";
            if ("SUCCESS".equals(currentPublishStatus) && review.getPublishedUrl() != null && !review.getPublishedUrl().isBlank()) {
                throw new IllegalArgumentException("이미 creation.kr에 게시된 문서입니다.");
            }
            if ("PENDING".equals(currentPublishStatus)) {
                throw new IllegalArgumentException("게시가 진행 중입니다. 잠시 후 다시 시도해주세요.");
            }

            String sitePath = request != null ? request.getSitePath() : null;
            String boardId = request != null ? request.getBoardId() : null;
            if (sitePath != null && !sitePath.isBlank() && boardId != null && !boardId.isBlank()) {
                if (!creationKrBoardCatalogService.isValidBoard(sitePath, boardId)) {
                    throw new IllegalArgumentException("선택한 creation.kr 게시판이 유효하지 않습니다.");
                }
            } else if ((sitePath != null && !sitePath.isBlank()) || (boardId != null && !boardId.isBlank())) {
                throw new IllegalArgumentException("sitePath와 boardId를 함께 지정해주세요.");
            }

            review.setPublishStatus("PENDING");
            review.setPublishError(null);
            reviewRepository.saveAndFlush(review);

            return new PublishBoardSelection(sitePath, boardId);
        });
    }

    private Review loadReviewForPublish(Long reviewId) {
        TransactionTemplate template = newReadOnlyTemplate();
        return template.execute(status -> {
            Review review = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다: " + reviewId));
            review.getDocument().getTitle();
            review.getDocumentVersion().getContent();
            return review;
        });
    }

    private ReviewResponse finalizePublishInNewTransaction(Long reviewId, PublishResult result) {
        TransactionTemplate template = newRequiresNewTemplate();
        return template.execute(status -> {
            Review review = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다: " + reviewId));

            if (!result.isSuccess()) {
                review.setPublishStatus("FAILED");
                review.setPublishError(result.getErrorMessage());
                Review failed = reviewRepository.save(review);
                return toResponse(failed);
            }

            review.setPublishStatus("SUCCESS");
            review.setPublishedUrl(result.getPublishedUrl());
            review.setPublishedAt(LocalDateTime.now());
            review.setPublishError(null);

            Document document = review.getDocument();
            document.setStatus("PUBLISHED");
            document.setPublishedUrl(result.getPublishedUrl());
            documentRepository.save(document);

            Review saved = reviewRepository.save(review);
            log.info("리뷰 creation.kr 게시 완료: 리뷰 ID {}, URL {}", reviewId, result.getPublishedUrl());
            return toResponse(saved);
        });
    }

    private TransactionTemplate newRequiresNewTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private TransactionTemplate newReadOnlyTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    private static final class PublishBoardSelection {
        private final String sitePath;
        private final String boardId;

        private PublishBoardSelection(String sitePath, String boardId) {
            this.sitePath = sitePath;
            this.boardId = boardId;
        }

        private String sitePath() {
            return sitePath;
        }

        private String boardId() {
            return boardId;
        }
    }

    @Transactional
    public ReviewResponse updateReview(Long reviewId, UpdateReviewRequest request, Long reviewerId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다: " + reviewId));

        assignReviewerIfNeeded(review, reviewerId);

        if (!"PENDING".equals(review.getStatus())) {
            throw new IllegalArgumentException("수정할 수 없는 상태입니다. 현재 상태: " + review.getStatus());
        }

        if (request.getComment() != null) {
            review.setComment(request.getComment());
        }
        if (request.getChecklist() != null) {
            try {
                String checklistJson = objectMapper.writeValueAsString(request.getChecklist());
                review.setChecklist(checklistJson);
            } catch (JsonProcessingException e) {
                log.error("체크리스트 JSON 변환 실패", e);
            }
        }
        if (request.getIsComplete() != null) {
            review.setIsComplete(request.getIsComplete());
        }

        Review saved = reviewRepository.save(review);
        log.info("리뷰 수정: 리뷰 ID {}", reviewId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findAll() {
        return reviewRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findByDocumentId(Long documentId) {
        return reviewRepository.findByDocument_Id(documentId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findByDocumentVersionId(Long documentVersionId) {
        return reviewRepository.findByDocumentVersion_Id(documentVersionId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findByReviewerId(Long reviewerId) {
        return reviewRepository.findByReviewer_Id(reviewerId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findByStatus(String status) {
        return reviewRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ReviewResponse> findById(Long id) {
        return reviewRepository.findById(id)
                .map(this::toResponse);
    }

    /**
     * 자동 생성된 리뷰(reviewer 미할당)는 승인/반려/수정 시점에 현재 관리자를 reviewer로 할당합니다.
     * 이미 할당된 리뷰는 본인만 처리할 수 있습니다.
     */
    private void assignReviewerIfNeeded(Review review, Long adminUserId) {
        User reviewer = review.getReviewer();
        if (reviewer == null) {
            User admin = userRepository.findById(adminUserId)
                    .orElseThrow(() -> new IllegalArgumentException("리뷰어를 찾을 수 없습니다: " + adminUserId));
            review.setReviewer(admin);
            return;
        }
        if (!reviewer.getId().equals(adminUserId)) {
            throw new IllegalArgumentException("본인의 리뷰만 처리할 수 있습니다.");
        }
    }

    private ReviewResponse toResponse(Review review) {
        // Checklist JSON 문자열을 Map으로 변환
        Map<String, Boolean> checklistMap = null;
        if (review.getChecklist() != null && !review.getChecklist().isEmpty()) {
            try {
                checklistMap = objectMapper.readValue(review.getChecklist(), new TypeReference<Map<String, Boolean>>() {});
            } catch (JsonProcessingException e) {
                log.error("체크리스트 JSON 파싱 실패", e);
                checklistMap = new HashMap<>();
            }
        }

        ReviewResponse.ReviewResponseBuilder builder = ReviewResponse.builder()
                .id(review.getId())
                .status(review.getStatus())
                .comment(review.getComment())
                .checklist(checklistMap)
                .reviewedAt(review.getReviewedAt())
                .finalApprovalAt(review.getFinalApprovalAt())
                .publishedAt(review.getPublishedAt())
                .publishedUrl(review.getPublishedUrl())
                .publishStatus(review.getPublishStatus())
                .publishError(review.getPublishError())
                .isComplete(review.getIsComplete())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt());

        // Document 정보
        if (review.getDocument() != null) {
            builder.document(ReviewResponse.DocumentInfo.builder()
                    .id(review.getDocument().getId())
                    .title(review.getDocument().getTitle())
                    .build());
        }

        // DocumentVersion 정보
        if (review.getDocumentVersion() != null) {
            builder.documentVersion(ReviewResponse.VersionInfo.builder()
                    .id(review.getDocumentVersion().getId())
                    .versionNumber(review.getDocumentVersion().getVersionNumber())
                    .versionType(review.getDocumentVersion().getVersionType())
                    .build());
        }

        // Reviewer 정보
        if (review.getReviewer() != null) {
            builder.reviewer(ReviewResponse.ReviewerInfo.builder()
                    .id(review.getReviewer().getId())
                    .email(review.getReviewer().getEmail())
                    .name(review.getReviewer().getName())
                    .build());
        }

        // 담당 번역가 정보 (검토 대상 버전의 생성자)
        if (review.getDocumentVersion() != null && review.getDocumentVersion().getCreatedBy() != null) {
            var createdBy = review.getDocumentVersion().getCreatedBy();
            builder.translator(ReviewResponse.TranslatorInfo.builder()
                    .id(createdBy.getId())
                    .email(createdBy.getEmail())
                    .name(createdBy.getName())
                    .build());
        }

        return builder.build();
    }
}

