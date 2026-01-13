package com.project.Transflow.document.controller;

import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.document.dto.CreateDocumentVersionRequest;
import com.project.Transflow.document.dto.DocumentVersionResponse;
import com.project.Transflow.document.service.DocumentVersionService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/documents/{documentId}/versions")
@RequiredArgsConstructor
@Tag(name = "문서 버전 API", description = "문서 버전 관리 API")
@SecurityRequirement(name = "JWT")
public class DocumentVersionController {

    private final DocumentVersionService documentVersionService;
    private final AdminAuthUtil adminAuthUtil;

    @Operation(
            summary = "문서 버전 생성",
            description = "문서의 새 버전을 생성합니다. Version 0: 원문, Version 1: AI 초벌, Version 2+: 수동 번역"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "버전 생성 성공",
                    content = @Content(schema = @Schema(implementation = DocumentVersionResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    @PostMapping
    public ResponseEntity<DocumentVersionResponse> createVersion(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId,
            @Valid @RequestBody CreateDocumentVersionRequest request) {

        // 개발 단계: 헤더가 없으면 기본 사용자 ID 사용 (null 허용)
        Long createdById = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            createdById = adminAuthUtil.getUserIdFromToken(authHeader);
        }
        // 헤더가 없으면 createdById는 null로 전달 (서비스에서 처리)

        try {
            DocumentVersionResponse response = documentVersionService.createVersion(documentId, request, createdById);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
            summary = "문서 버전 목록 조회",
            description = "문서의 모든 버전 목록을 조회합니다. (버전 번호 순서)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public ResponseEntity<List<DocumentVersionResponse>> getAllVersions(
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId) {

        List<DocumentVersionResponse> versions = documentVersionService.findAllByDocumentId(documentId);
        return ResponseEntity.ok(versions);
    }

    @Operation(
            summary = "현재 버전 조회",
            description = "문서의 현재 활성 버전을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DocumentVersionResponse.class))),
            @ApiResponse(responseCode = "404", description = "버전을 찾을 수 없음")
    })
    @GetMapping("/current")
    public ResponseEntity<DocumentVersionResponse> getCurrentVersion(
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId) {

        return documentVersionService.findCurrentVersion(documentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "최종 버전 조회",
            description = "문서의 최종 버전을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DocumentVersionResponse.class))),
            @ApiResponse(responseCode = "404", description = "최종 버전을 찾을 수 없음")
    })
    @GetMapping("/final")
    public ResponseEntity<DocumentVersionResponse> getFinalVersion(
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId) {

        return documentVersionService.findFinalVersion(documentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "특정 버전 번호로 조회",
            description = "버전 번호로 특정 버전을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DocumentVersionResponse.class))),
            @ApiResponse(responseCode = "404", description = "버전을 찾을 수 없음")
    })
    @GetMapping("/version/{versionNumber}")
    public ResponseEntity<DocumentVersionResponse> getVersionByNumber(
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId,
            @Parameter(description = "버전 번호", required = true, example = "0")
            @PathVariable Integer versionNumber) {

        return documentVersionService.findByVersionNumber(documentId, versionNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "버전 상세 조회",
            description = "버전 ID로 버전 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DocumentVersionResponse.class))),
            @ApiResponse(responseCode = "404", description = "버전을 찾을 수 없음")
    })
    @GetMapping("/{versionId}")
    public ResponseEntity<DocumentVersionResponse> getVersionById(
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId,
            @Parameter(description = "버전 ID", required = true, example = "1")
            @PathVariable Long versionId) {

        return documentVersionService.findById(versionId)
                .filter(version -> version.getDocumentId().equals(documentId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "현재 버전 설정",
            description = "특정 버전을 현재 활성 버전으로 설정합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "설정 성공",
                    content = @Content(schema = @Schema(implementation = DocumentVersionResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "문서 또는 버전을 찾을 수 없음")
    })
    @PutMapping("/{versionId}/set-current")
    public ResponseEntity<DocumentVersionResponse> setAsCurrentVersion(
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId,
            @Parameter(description = "버전 ID", required = true, example = "1")
            @PathVariable Long versionId) {

        try {
            DocumentVersionResponse response = documentVersionService.setAsCurrentVersion(documentId, versionId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}

