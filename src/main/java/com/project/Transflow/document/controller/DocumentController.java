package com.project.Transflow.document.controller;

import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.document.dto.CompleteTranslationRequest;
import com.project.Transflow.document.dto.CreateDocumentRequest;
import com.project.Transflow.document.dto.CreateDocumentVersionRequest;
import com.project.Transflow.document.dto.DocumentResponse;
import com.project.Transflow.document.dto.HandoverRequest;
import com.project.Transflow.document.dto.UpdateDocumentRequest;
import com.project.Transflow.document.service.DocumentService;
import com.project.Transflow.document.service.HandoverHistoryService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "문서 API", description = "문서 관리 API")
@SecurityRequirement(name = "JWT")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentVersionService versionService;
    private final HandoverHistoryService handoverHistoryService;
    private final AdminAuthUtil adminAuthUtil;
    private final UserRepository userRepository;

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
            Integer roleLevel = adminAuthUtil.getRoleLevelFromToken(authHeader);
            log.info("문서 생성 요청 - roleLevel: {}, authHeader: {}", roleLevel, authHeader != null ? "present" : "null");
            
            if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
                log.warn("권한 없음 - roleLevel: {}", roleLevel);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(null);
            }
            createdById = adminAuthUtil.getUserIdFromToken(authHeader);
            log.info("문서 생성 승인 - userId: {}, roleLevel: {}", createdById, roleLevel);
        } else {
            log.warn("Authorization 헤더가 없습니다. 기본 사용자로 진행합니다.");
        }
        // 헤더가 없으면 createdById는 null로 전달 (서비스에서 처리)

        DocumentResponse response = documentService.createDocument(request, createdById);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "문서 목록 조회",
            description = "모든 문서 목록을 조회합니다. 검색 및 필터링 지원"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getAllDocuments(
            @Parameter(description = "상태 필터", example = "PENDING_TRANSLATION")
            @RequestParam(required = false) String status,
            @Parameter(description = "카테고리 ID 필터", example = "1")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "PENDING_TRANSLATION 상태 제외 여부", example = "true")
            @RequestParam(required = false, defaultValue = "false") Boolean excludePendingTranslation,
            @Parameter(description = "원문만 조회(복사본 제외). 번역 대기 목록에서 원문이 사라지지 않도록 할 때 사용", example = "true")
            @RequestParam(required = false, defaultValue = "false") Boolean sourcesOnly,
            @Parameter(description = "제목 검색", example = "문서 제목")
            @RequestParam(required = false) String title) {

        List<DocumentResponse> documents;
        
        // 원문만 조회: URL 중복 제거 없이 원문만 반환 (번역 대기 목록에서 누가 작업을 시작해도 원문이 계속 보이도록)
        if (Boolean.TRUE.equals(sourcesOnly)) {
            documents = documentService.findSourceDocumentsOnly();
        } else if (title != null && !title.trim().isEmpty()) {
        // 제목 검색이 있으면 검색 결과 사용
            if (status != null && "PENDING_TRANSLATION".equals(status)) {
                documents = documentService.findPendingTranslationSourcesNotFinalized().stream()
                        .filter(doc -> doc.getTitle() != null && doc.getTitle().toLowerCase().contains(title.trim().toLowerCase()))
                        .collect(java.util.stream.Collectors.toList());
            } else {
                documents = documentService.findByTitleContaining(title.trim());
                if (status != null) {
                    documents = documents.stream()
                            .filter(doc -> doc.getStatus().equals(status))
                            .collect(java.util.stream.Collectors.toList());
                }
            }
            if (categoryId != null) {
                documents = documents.stream()
                        .filter(doc -> doc.getCategoryId() != null && doc.getCategoryId().equals(categoryId))
                        .collect(java.util.stream.Collectors.toList());
            }
        } else if (status != null && categoryId != null) {
            // 상태와 카테고리로 필터링
            documents = "PENDING_TRANSLATION".equals(status)
                    ? documentService.findPendingTranslationSourcesNotFinalized()
                    : documentService.findByStatus(status);
            documents = documents.stream()
                    .filter(doc -> doc.getCategoryId() != null && doc.getCategoryId().equals(categoryId))
                    .collect(java.util.stream.Collectors.toList());
        } else if (status != null) {
            documents = "PENDING_TRANSLATION".equals(status)
                    ? documentService.findPendingTranslationSourcesNotFinalized()
                    : documentService.findByStatus(status);
        } else if (categoryId != null) {
            documents = documentService.findByCategoryId(categoryId);
        } else {
            // excludePendingTranslation이 true이면 PENDING_TRANSLATION 제외
            if (excludePendingTranslation) {
                documents = documentService.findAllExcludingPendingTranslation();
            } else {
                documents = documentService.findAll();
            }
        }

        return ResponseEntity.ok(documents);
    }

    @Operation(
            summary = "URL 중복 체크",
            description = "해당 URL로 이미 문서가 존재하는지 확인합니다. 초벌 번역 중복 방지용."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/check-url")
    public ResponseEntity<Map<String, Object>> checkUrlExists(
            @Parameter(description = "검사할 URL", required = true) @RequestParam String url) {
        boolean exists = documentService.existsByOriginalUrl(url);
        return ResponseEntity.ok(Map.of("exists", exists));
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
            summary = "원문의 복사본 목록 조회",
            description = "해당 원문에서 파생된 복사본(다른 사람 작업 중인 번역) 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/{sourceDocumentId}/copies")
    public ResponseEntity<List<DocumentResponse>> getCopiesBySourceId(
            @Parameter(description = "원문 문서 ID", required = true) @PathVariable Long sourceDocumentId) {
        return ResponseEntity.ok(documentService.findCopiesBySourceDocumentId(sourceDocumentId));
    }

    @Operation(
            summary = "내 복사본 조회",
            description = "현재 로그인 사용자가 해당 원문에서 만든 복사본이 있는지 조회합니다. 번역 시작 전 중복 방지용."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "복사본 있음", content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "404", description = "복사본 없음")
    })
    @GetMapping("/{sourceDocumentId}/my-copy")
    public ResponseEntity<DocumentResponse> getMyCopyBySourceId(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "원문 문서 ID", required = true) @PathVariable Long sourceDocumentId) {
        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            try {
                userId = adminAuthUtil.getUserIdFromToken(authHeader);
            } catch (Exception e) {
                log.warn("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
            }
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return documentService.findMyCopyBySourceId(sourceDocumentId, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "번역 시작 (복사본 생성)",
            description = "원문 문서에서 번역용 복사본을 생성하고 해당 문서로 작업을 시작합니다. 봉사자/관리자 모두 사용 가능. 락 없이 본인 전용 문서가 생성됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "복사본 생성 성공",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "원문이 아니거나 잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "원문 문서를 찾을 수 없음")
    })
    @PostMapping("/{documentId}/start-translation")
    public ResponseEntity<DocumentResponse> startTranslation(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "원문 문서 ID", required = true, example = "1")
            @PathVariable Long documentId) {

        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            try {
                userId = adminAuthUtil.getUserIdFromToken(authHeader);
            } catch (Exception e) {
                log.warn("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
            }
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        DocumentResponse response = documentService.createCopyForTranslation(documentId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "이어서 작업 (복사본 생성, 관리자 전용)",
            description = "다른 사용자의 문서를 이어받아 새 복사본을 생성합니다. 관리자만 호출 가능합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "복사본 생성 성공",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
            @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    @PostMapping("/{documentId}/copy-for-continuation")
    public ResponseEntity<DocumentResponse> copyForContinuation(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "이어받을 문서 ID", required = true, example = "1")
            @PathVariable Long documentId) {

        if (authHeader == null || authHeader.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Long userId = adminAuthUtil.getUserIdFromToken(authHeader);
        DocumentResponse response = documentService.createCopyForContinuation(documentId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "번역 완료",
            description = "번역 작업을 완료하고 검토 대기 상태로 변경합니다. (락 없이 문서에 직접 저장)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "완료 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/{documentId}/complete")
    public ResponseEntity<Map<String, Object>> completeTranslation(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "문서 ID", required = true) @PathVariable Long documentId,
            @Valid @RequestBody CompleteTranslationRequest request) {

        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            try {
                userId = adminAuthUtil.getUserIdFromToken(authHeader);
            } catch (Exception e) {
                log.warn("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
            }
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        CreateDocumentVersionRequest versionRequest = new CreateDocumentVersionRequest();
        versionRequest.setVersionType("MANUAL_TRANSLATION");
        versionRequest.setContent(request.getContent());
        versionRequest.setIsFinal(false);
        versionService.createVersion(documentId, versionRequest, userId);

        UpdateDocumentRequest updateRequest = new UpdateDocumentRequest();
        updateRequest.setStatus("PENDING_REVIEW");
        updateRequest.setCompletedParagraphs(request.getCompletedParagraphs());
        documentService.updateDocument(documentId, updateRequest, userId);

        return ResponseEntity.ok(Map.of("success", true, "message", "번역이 완료되었습니다.", "status", "PENDING_REVIEW"));
    }

    @Operation(
            summary = "임시 저장",
            description = "번역 작업 중 완료된 문단 정보를 문서에 임시 저장합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PutMapping("/{documentId}/translation")
    public ResponseEntity<Map<String, Object>> saveTranslation(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "문서 ID", required = true) @PathVariable Long documentId,
            @Valid @RequestBody CompleteTranslationRequest request) {

        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            try {
                userId = adminAuthUtil.getUserIdFromToken(authHeader);
            } catch (Exception e) {
                log.warn("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
            }
        }
        if (userId == null) {
            userId = userRepository.findAll().stream()
                    .filter(u -> u.getRoleLevel() != null && u.getRoleLevel() <= 2)
                    .findFirst()
                    .map(User::getId)
                    .orElseGet(() -> userRepository.findAll().isEmpty() ? null : userRepository.findAll().get(0).getId());
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("success", false, "message", "시스템에 사용자가 없습니다. 먼저 로그인하거나 사용자를 생성해주세요."));
            }
            log.warn("Authorization 없음: 기본 사용자 ID {} 사용", userId);
        }
        CreateDocumentVersionRequest versionRequest = new CreateDocumentVersionRequest();
        versionRequest.setVersionType("MANUAL_TRANSLATION");
        versionRequest.setContent(request.getContent());
        versionRequest.setIsFinal(false);
        versionService.createVersion(documentId, versionRequest, userId);

        UpdateDocumentRequest updateRequest = new UpdateDocumentRequest();
        updateRequest.setCompletedParagraphs(request.getCompletedParagraphs());
        documentService.updateDocument(documentId, updateRequest, userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "저장되었습니다."));
    }

    @Operation(
            summary = "인계 요청",
            description = "번역 중인 문서에 인계 메모를 남깁니다. 인계 요청 문서 목록에 표시됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인계 요청 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    @PostMapping("/{documentId}/handover")
    public ResponseEntity<Map<String, Object>> handover(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "문서 ID", required = true) @PathVariable Long documentId,
            @Valid @RequestBody HandoverRequest request) {

        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            try {
                userId = adminAuthUtil.getUserIdFromToken(authHeader);
            } catch (Exception e) {
                log.warn("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
            }
        }
        handoverHistoryService.createHandover(documentId, request, userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "인계 요청이 등록되었습니다."));
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

