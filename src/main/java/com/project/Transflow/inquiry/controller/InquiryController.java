package com.project.Transflow.inquiry.controller;

import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.inquiry.dto.*;
import com.project.Transflow.inquiry.service.InquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
@Tag(name = "문의 게시판", description = "문의 및 관리자 답변 API")
@SecurityRequirement(name = "JWT")
public class InquiryController {

    private final InquiryService inquiryService;
    private final AdminAuthUtil adminAuthUtil;

    @Operation(summary = "사이드바 배지용 카운트")
    @GetMapping("/badge-counts")
    public ResponseEntity<InquiryBadgeCountsResponse> getBadgeCounts(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ResponseEntity.ok(inquiryService.getBadgeCounts(authHeader));
    }

    @Operation(summary = "문의 목록")
    @GetMapping
    public ResponseEntity<Page<InquirySummaryResponse>> list(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false, defaultValue = "false") boolean mine,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long authorFilter = null;
        if (mine) {
            Long uid = adminAuthUtil.getUserIdFromToken(authHeader);
            if (uid == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            authorFilter = uid;
        }
        Long viewerId = adminAuthUtil.getUserIdFromToken(authHeader);
        return ResponseEntity.ok(inquiryService.list(pageable, authorFilter, viewerId));
    }

    @Operation(summary = "문의 상세 (markRead=true이고 작성자면 새 답변 읽음 처리 — 상세 페이지에서만 true 권장)")
    @GetMapping("/{id}")
    public ResponseEntity<InquiryDetailResponse> getDetail(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false, defaultValue = "true") boolean markRead,
            @PathVariable Long id) {
        Long viewerId = adminAuthUtil.getUserIdFromToken(authHeader);
        try {
            return ResponseEntity.ok(inquiryService.getDetail(id, viewerId, markRead));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "문의 작성")
    @PostMapping
    public ResponseEntity<?> create(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreateInquiryRequest request) {
        Long userId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("로그인이 필요합니다."));
        }
        try {
            return ResponseEntity.ok(inquiryService.createInquiry(request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @Operation(summary = "문의 수정 (답변 전만)")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @Valid @RequestBody UpdateInquiryRequest request) {
        Long userId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("로그인이 필요합니다."));
        }
        try {
            return ResponseEntity.ok(inquiryService.updateInquiry(id, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @Operation(summary = "문의 삭제 (답변 전만)")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Long userId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("로그인이 필요합니다."));
        }
        try {
            inquiryService.deleteInquiry(id, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @Operation(summary = "답변 작성 (관리자)")
    @PostMapping("/{id}/replies")
    public ResponseEntity<?> createReply(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @Valid @RequestBody CreateReplyRequest request) {
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("관리자만 답변할 수 있습니다."));
        }
        Long userId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("로그인이 필요합니다."));
        }
        try {
            return ResponseEntity.ok(inquiryService.createReply(id, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @Operation(summary = "답변 수정 (작성 관리자)")
    @PutMapping("/{id}/replies/{replyId}")
    public ResponseEntity<?> updateReply(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @PathVariable Long replyId,
            @Valid @RequestBody UpdateReplyRequest request) {
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("관리자만 수정할 수 있습니다."));
        }
        Long userId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("로그인이 필요합니다."));
        }
        try {
            return ResponseEntity.ok(inquiryService.updateReply(id, replyId, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @Operation(summary = "답변 삭제 (작성 관리자, 소프트 삭제)")
    @DeleteMapping("/{id}/replies/{replyId}")
    public ResponseEntity<?> deleteReply(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @PathVariable Long replyId) {
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("관리자만 삭제할 수 있습니다."));
        }
        Long userId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("로그인이 필요합니다."));
        }
        try {
            inquiryService.deleteReply(id, replyId, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    private java.util.Map<String, String> error(String msg) {
        return java.util.Map.of("error", msg);
    }
}
