package com.project.Transflow.review.controller;

import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.review.dto.CreateReviewRequest;
import com.project.Transflow.review.dto.ReviewResponse;
import com.project.Transflow.review.dto.UpdateReviewRequest;
import com.project.Transflow.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "리뷰 API", description = "리뷰 및 승인 관리 API")
@SecurityRequirement(name = "JWT")
public class ReviewController {

    private final ReviewService reviewService;
    private final AdminAuthUtil adminAuthUtil;

    @Operation(
            summary = "리뷰 생성",
            description = "새로운 리뷰를 생성합니다. 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "리뷰 생성 성공",
                    content = @Content(schema = @Schema(implementation = ReviewResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (이미 존재하는 리뷰 등)"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @ApiResponse(responseCode = "404", description = "문서 또는 버전을 찾을 수 없음")
    })
    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreateReviewRequest request) {

        // 권한 체크 (관리자 이상)
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Long reviewerId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (reviewerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ReviewResponse response = reviewService.createReview(request, reviewerId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
            summary = "리뷰 승인",
            description = "리뷰를 승인하고 최종 버전으로 설정합니다. 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 성공",
                    content = @Content(schema = @Schema(implementation = ReviewResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (권한 없음, 상태 오류 등)"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @ApiResponse(responseCode = "404", description = "리뷰를 찾을 수 없음")
    })
    @PostMapping("/{id}/approve")
    public ResponseEntity<ReviewResponse> approveReview(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "리뷰 ID", required = true, example = "1")
            @PathVariable Long id) {

        // 권한 체크 (관리자 이상)
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Long reviewerId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (reviewerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ReviewResponse response = reviewService.approveReview(id, reviewerId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
            summary = "리뷰 반려",
            description = "리뷰를 반려합니다. 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 성공",
                    content = @Content(schema = @Schema(implementation = ReviewResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (권한 없음, 상태 오류 등)"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @ApiResponse(responseCode = "404", description = "리뷰를 찾을 수 없음")
    })
    @PostMapping("/{id}/reject")
    public ResponseEntity<ReviewResponse> rejectReview(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "리뷰 ID", required = true, example = "1")
            @PathVariable Long id) {

        // 권한 체크 (관리자 이상)
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Long reviewerId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (reviewerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ReviewResponse response = reviewService.rejectReview(id, reviewerId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
            summary = "리뷰 게시",
            description = "승인된 리뷰를 게시합니다. (creation.kr) 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "게시 성공",
                    content = @Content(schema = @Schema(implementation = ReviewResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (승인되지 않은 리뷰 등)"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @ApiResponse(responseCode = "404", description = "리뷰를 찾을 수 없음")
    })
    @PostMapping("/{id}/publish")
    public ResponseEntity<ReviewResponse> publishReview(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "리뷰 ID", required = true, example = "1")
            @PathVariable Long id) {

        // 권한 체크 (관리자 이상)
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Long reviewerId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (reviewerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ReviewResponse response = reviewService.publishReview(id, reviewerId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
            summary = "리뷰 수정",
            description = "리뷰 정보를 수정합니다. (PENDING 상태만 가능) 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = ReviewResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (권한 없음, 상태 오류 등)"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @ApiResponse(responseCode = "404", description = "리뷰를 찾을 수 없음")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ReviewResponse> updateReview(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "리뷰 ID", required = true, example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateReviewRequest request) {

        // 권한 체크 (관리자 이상)
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Long reviewerId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (reviewerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ReviewResponse response = reviewService.updateReview(id, request, reviewerId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
            summary = "리뷰 목록 조회",
            description = "리뷰 목록을 조회합니다. 필터링 가능"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public ResponseEntity<List<ReviewResponse>> getAllReviews(
            @Parameter(description = "문서 ID 필터", example = "1")
            @RequestParam(required = false) Long documentId,
            @Parameter(description = "문서 버전 ID 필터", example = "2")
            @RequestParam(required = false) Long documentVersionId,
            @Parameter(description = "리뷰어 ID 필터", example = "1")
            @RequestParam(required = false) Long reviewerId,
            @Parameter(description = "상태 필터", example = "APPROVED")
            @RequestParam(required = false) String status) {

        List<ReviewResponse> reviews;

        if (documentId != null && status != null) {
            reviews = reviewService.findByDocumentId(documentId).stream()
                    .filter(r -> r.getStatus().equals(status))
                    .collect(java.util.stream.Collectors.toList());
        } else if (documentId != null) {
            reviews = reviewService.findByDocumentId(documentId);
        } else if (documentVersionId != null) {
            reviews = reviewService.findByDocumentVersionId(documentVersionId);
        } else if (reviewerId != null) {
            reviews = reviewService.findByReviewerId(reviewerId);
        } else if (status != null) {
            reviews = reviewService.findByStatus(status);
        } else {
            reviews = reviewService.findAll();
        }

        return ResponseEntity.ok(reviews);
    }

    @Operation(
            summary = "리뷰 상세 조회",
            description = "리뷰 ID로 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ReviewResponse.class))),
            @ApiResponse(responseCode = "404", description = "리뷰를 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ReviewResponse> getReviewById(
            @Parameter(description = "리뷰 ID", required = true, example = "1")
            @PathVariable Long id) {

        return reviewService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

