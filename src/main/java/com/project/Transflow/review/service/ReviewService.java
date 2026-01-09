package com.project.Transflow.review.service;

import com.project.Transflow.document.entity.Document;
import com.project.Transflow.document.entity.DocumentVersion;
import com.project.Transflow.document.repository.DocumentRepository;
import com.project.Transflow.document.repository.DocumentVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.Transflow.review.dto.CreateReviewRequest;
import com.project.Transflow.review.dto.ReviewResponse;
import com.project.Transflow.review.dto.UpdateReviewRequest;
import com.project.Transflow.review.entity.Review;
import com.project.Transflow.review.repository.ReviewRepository;
import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    @Transactional
    public ReviewResponse approveReview(Long reviewId, Long reviewerId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다: " + reviewId));

        // 권한 체크: 본인의 리뷰인지 확인
        if (!review.getReviewer().getId().equals(reviewerId)) {
            throw new IllegalArgumentException("본인의 리뷰만 승인할 수 있습니다.");
        }

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

        // 권한 체크
        if (!review.getReviewer().getId().equals(reviewerId)) {
            throw new IllegalArgumentException("본인의 리뷰만 반려할 수 있습니다.");
        }

        if (!"PENDING".equals(review.getStatus())) {
            throw new IllegalArgumentException("반려할 수 없는 상태입니다. 현재 상태: " + review.getStatus());
        }

        review.setStatus("REJECTED");
        review.setReviewedAt(LocalDateTime.now());

        // Document 상태 업데이트 (다시 번역 필요)
        Document document = review.getDocument();
        document.setStatus("IN_TRANSLATION");
        documentRepository.save(document);

        Review saved = reviewRepository.save(review);
        log.info("리뷰 반려: 리뷰 ID {}", reviewId);
        return toResponse(saved);
    }

    @Transactional
    public ReviewResponse publishReview(Long reviewId, Long reviewerId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다: " + reviewId));

        // 권한 체크
        if (!review.getReviewer().getId().equals(reviewerId)) {
            throw new IllegalArgumentException("본인의 리뷰만 게시할 수 있습니다.");
        }

        if (!"APPROVED".equals(review.getStatus())) {
            throw new IllegalArgumentException("승인된 리뷰만 게시할 수 있습니다. 현재 상태: " + review.getStatus());
        }

        review.setPublishedAt(LocalDateTime.now());

        // Document 상태 업데이트
        Document document = review.getDocument();
        document.setStatus("PUBLISHED");
        documentRepository.save(document);

        Review saved = reviewRepository.save(review);
        log.info("리뷰 게시: 리뷰 ID {}", reviewId);
        return toResponse(saved);
    }

    @Transactional
    public ReviewResponse updateReview(Long reviewId, UpdateReviewRequest request, Long reviewerId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다: " + reviewId));

        // 권한 체크
        if (!review.getReviewer().getId().equals(reviewerId)) {
            throw new IllegalArgumentException("본인의 리뷰만 수정할 수 있습니다.");
        }

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

        return builder.build();
    }
}

