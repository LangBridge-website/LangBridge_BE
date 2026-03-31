package com.project.Transflow.document.controller;

import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.document.dto.DocumentResponse;
import com.project.Transflow.document.service.DocumentFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "문서 찜 API", description = "문서 찜 관리 API")
@SecurityRequirement(name = "JWT")
public class DocumentFavoriteController {

    private final DocumentFavoriteService favoriteService;
    private final AdminAuthUtil adminAuthUtil;

    @Operation(
            summary = "문서 찜 추가",
            description = "문서를 찜 목록에 추가합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "찜 추가 성공"),
            @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    @PostMapping("/{documentId}/favorite")
    public ResponseEntity<Map<String, Object>> addFavorite(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId) {

        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            try {
                userId = adminAuthUtil.getUserIdFromToken(authHeader);
            } catch (Exception e) {
                log.warn("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "인증이 필요합니다."));
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "인증이 필요합니다."));
        }

        try {
            favoriteService.addFavorite(userId, documentId);
            return ResponseEntity.ok(Map.of("success", true, "message", "찜 목록에 추가되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @Operation(
            summary = "문서 찜 제거",
            description = "문서를 찜 목록에서 제거합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "찜 제거 성공")
    })
    @DeleteMapping("/{documentId}/favorite")
    public ResponseEntity<Map<String, Object>> removeFavorite(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId) {

        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            try {
                userId = adminAuthUtil.getUserIdFromToken(authHeader);
            } catch (Exception e) {
                log.warn("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "인증이 필요합니다."));
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "인증이 필요합니다."));
        }

        favoriteService.removeFavorite(userId, documentId);
        return ResponseEntity.ok(Map.of("success", true, "message", "찜 목록에서 제거되었습니다."));
    }

    @Operation(
            summary = "찜한 문서 목록 조회",
            description = "현재 사용자가 찜한 문서 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/favorites")
    public ResponseEntity<List<DocumentResponse>> getFavoriteDocuments(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            try {
                userId = adminAuthUtil.getUserIdFromToken(authHeader);
            } catch (Exception e) {
                log.warn("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<DocumentResponse> favorites = favoriteService.getFavoriteDocuments(userId);
        return ResponseEntity.ok(favorites);
    }

    @Operation(
            summary = "문서 찜 여부 일괄 조회",
            description = "주어진 문서 id 목록 중, 현재 사용자가 찜한 문서 id만 반환합니다. (목록 화면 N+1 방지)"
    )
    @PostMapping("/favorites/bulk-status")
    public ResponseEntity<Map<String, Object>> getFavoriteBulkStatus(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, List<Long>> body) {

        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            try {
                userId = adminAuthUtil.getUserIdFromToken(authHeader);
            } catch (Exception e) {
                log.warn("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Long> documentIds = body != null && body.get("documentIds") != null
                ? body.get("documentIds")
                : List.of();

        Set<Long> favoriteIds = favoriteService.findFavoriteDocumentIdsIn(userId, documentIds);
        Map<String, Object> res = new HashMap<>();
        res.put("favoriteDocumentIds", new ArrayList<>(favoriteIds));
        return ResponseEntity.ok(res);
    }

    @Operation(
            summary = "문서 찜 여부 확인",
            description = "특정 문서가 찜 목록에 있는지 확인합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/{documentId}/favorite")
    public ResponseEntity<Map<String, Object>> isFavorite(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId) {

        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            try {
                userId = adminAuthUtil.getUserIdFromToken(authHeader);
            } catch (Exception e) {
                log.warn("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("isFavorite", false));
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("isFavorite", false));
        }

        boolean isFavorite = favoriteService.isFavorite(userId, documentId);
        return ResponseEntity.ok(Map.of("isFavorite", isFavorite));
    }
}


