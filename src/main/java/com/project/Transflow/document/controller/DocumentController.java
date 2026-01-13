package com.project.Transflow.document.controller;

import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.document.dto.CreateDocumentRequest;
import com.project.Transflow.document.dto.DocumentResponse;
import com.project.Transflow.document.dto.UpdateDocumentRequest;
import com.project.Transflow.document.service.DocumentService;
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
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "문서 API", description = "문서 관리 API")
@SecurityRequirement(name = "JWT")
public class DocumentController {

    private final DocumentService documentService;
    private final AdminAuthUtil adminAuthUtil;

    @Operation(
            summary = "문서 생성",
            description = "새로운 문서를 생성합니다. 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문서 생성 성공",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)")
    })
    @PostMapping
    public ResponseEntity<DocumentResponse> createDocument(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody CreateDocumentRequest request) {

        // 개발 단계: 헤더가 없으면 기본 사용자 ID 사용 (null 허용)
        Long createdById = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            // 권한 체크 (관리자 이상) - 헤더가 있을 때만
            if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            createdById = adminAuthUtil.getUserIdFromToken(authHeader);
        }
        // 헤더가 없으면 createdById는 null로 전달 (서비스에서 처리)

        DocumentResponse response = documentService.createDocument(request, createdById);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "문서 목록 조회",
            description = "모든 문서 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getAllDocuments(
            @Parameter(description = "상태 필터", example = "PENDING_TRANSLATION")
            @RequestParam(required = false) String status,
            @Parameter(description = "카테고리 ID 필터", example = "1")
            @RequestParam(required = false) Long categoryId) {

        List<DocumentResponse> documents;
        if (status != null && categoryId != null) {
            // 상태와 카테고리로 필터링 (Repository에 메서드 추가 필요)
            documents = documentService.findByStatus(status);
            documents = documents.stream()
                    .filter(doc -> doc.getCategoryId() != null && doc.getCategoryId().equals(categoryId))
                    .collect(java.util.stream.Collectors.toList());
        } else if (status != null) {
            documents = documentService.findByStatus(status);
        } else if (categoryId != null) {
            documents = documentService.findByCategoryId(categoryId);
        } else {
            documents = documentService.findAll();
        }

        return ResponseEntity.ok(documents);
    }

    @Operation(
            summary = "문서 상세 조회",
            description = "문서 ID로 문서 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocumentById(
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long id) {

        return documentService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "문서 수정",
            description = "문서 정보를 수정합니다. 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> updateDocument(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentRequest request) {

        // 권한 체크 (관리자 이상)
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Long modifiedById = adminAuthUtil.getUserIdFromToken(authHeader);
        DocumentResponse response = documentService.updateDocument(id, request, modifiedById);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "문서 삭제",
            description = "문서를 삭제합니다. 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long id) {

        // 권한 체크 (관리자 이상)
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        documentService.deleteDocument(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "문서가 삭제되었습니다."));
    }
}

