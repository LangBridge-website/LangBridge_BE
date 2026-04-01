package com.project.Transflow.document.controller;

import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.document.dto.CreateDocumentCommentRequest;
import com.project.Transflow.document.dto.DocumentCommentResponse;
import com.project.Transflow.document.service.DocumentCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/documents/{documentId}/comments")
@RequiredArgsConstructor
@Tag(name = "문서 댓글 API", description = "문서별 소통 채팅 API")
@SecurityRequirement(name = "JWT")
public class DocumentCommentController {

    private final DocumentCommentService commentService;
    private final AdminAuthUtil adminAuthUtil;

    @Operation(summary = "댓글 목록 조회", description = "해당 문서의 댓글을 시간 오름차순으로 반환합니다.")
    @GetMapping
    public ResponseEntity<List<DocumentCommentResponse>> getComments(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long documentId) {
        Long userId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(commentService.getComments(documentId));
    }

    @Operation(summary = "댓글 작성", description = "로그인한 사용자로 댓글을 작성합니다.")
    @PostMapping
    public ResponseEntity<?> addComment(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long documentId,
            @Valid @RequestBody CreateDocumentCommentRequest request) {
        Long userId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요합니다."));
        }
        try {
            DocumentCommentResponse response = commentService.addComment(documentId, userId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @Operation(summary = "댓글 삭제", description = "본인 댓글 또는 관리자가 삭제합니다.")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<?> deleteComment(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long documentId,
            @PathVariable Long commentId) {
        Long userId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요합니다."));
        }
        Integer roleLevel = adminAuthUtil.getRoleLevelFromToken(authHeader);
        try {
            commentService.deleteComment(commentId, userId, roleLevel);
            return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }
}
