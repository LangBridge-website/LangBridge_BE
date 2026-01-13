package com.project.Transflow.document.controller;

import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.document.dto.*;
import com.project.Transflow.document.entity.DocumentLock;
import com.project.Transflow.document.service.DocumentLockService;
import com.project.Transflow.document.service.DocumentService;
import com.project.Transflow.document.service.DocumentVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/documents/{documentId}")
@RequiredArgsConstructor
@Tag(name = "문서 락 API", description = "문서 작업 락 관리 API")
public class DocumentLockController {

    private final DocumentLockService lockService;
    private final DocumentService documentService;
    private final DocumentVersionService versionService;
    private final AdminAuthUtil adminAuthUtil;

    @Operation(
            summary = "문서 락 획득",
            description = "번역 작업을 시작하기 위해 문서에 락을 획득합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "락 획득 성공"),
            @ApiResponse(responseCode = "409", description = "다른 사용자가 이미 락을 보유 중"),
            @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    @PostMapping("/lock")
    public ResponseEntity<LockStatusResponse> acquireLock(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId) {

        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            userId = adminAuthUtil.getUserIdFromToken(authHeader);
        } else {
            // 개발 단계: 기본 사용자 사용
            userId = 1L; // 임시
        }

        DocumentLock lock = lockService.acquireLock(documentId, userId);

        LockStatusResponse response = LockStatusResponse.builder()
                .locked(true)
                .lockedBy(LockStatusResponse.LockedByInfo.builder()
                        .id(lock.getLockedBy().getId())
                        .name(lock.getLockedBy().getName())
                        .email(lock.getLockedBy().getEmail())
                        .build())
                .lockedAt(lock.getLockedAt())
                .canEdit(true)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "문서 락 상태 확인",
            description = "문서의 현재 락 상태를 확인합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/lock-status")
    public ResponseEntity<LockStatusResponse> getLockStatus(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId) {

        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            userId = adminAuthUtil.getUserIdFromToken(authHeader);
        } else {
            userId = 1L; // 임시
        }

        var lockOpt = lockService.getLockStatus(documentId);
        if (lockOpt.isEmpty()) {
            LockStatusResponse response = LockStatusResponse.builder()
                    .locked(false)
                    .canEdit(false)
                    .build();
            return ResponseEntity.ok(response);
        }

        DocumentLock lock = lockOpt.get();
        boolean canEdit = lock.getLockedBy().getId().equals(userId);

        LockStatusResponse response = LockStatusResponse.builder()
                .locked(true)
                .lockedBy(LockStatusResponse.LockedByInfo.builder()
                        .id(lock.getLockedBy().getId())
                        .name(lock.getLockedBy().getName())
                        .email(lock.getLockedBy().getEmail())
                        .build())
                .lockedAt(lock.getLockedAt())
                .canEdit(canEdit)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "문서 락 해제",
            description = "번역 작업을 종료하고 락을 해제합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "락 해제 성공"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @DeleteMapping("/lock")
    public ResponseEntity<Map<String, Object>> releaseLock(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId) {

        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            userId = adminAuthUtil.getUserIdFromToken(authHeader);
        } else {
            userId = 1L; // 임시
        }

        lockService.releaseLock(documentId, userId);

        // 문서 상태를 PENDING_TRANSLATION으로 변경
        documentService.updateDocument(documentId, 
                UpdateDocumentRequest.builder().status("PENDING_TRANSLATION").build(), 
                userId);

        return ResponseEntity.ok(Map.of("success", true, "message", "락이 해제되었습니다."));
    }

    @Operation(
            summary = "관리자 락 강제 해제",
            description = "관리자 권한으로 문서 락을 강제로 해제합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "락 해제 성공"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 필요")
    })
    @DeleteMapping("/lock/admin")
    public ResponseEntity<Map<String, Object>> releaseLockByAdmin(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId) {

        if (authHeader == null || authHeader.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        }

        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
        }

        lockService.releaseLockByAdmin(documentId);

        return ResponseEntity.ok(Map.of("success", true, "message", "락이 강제 해제되었습니다."));
    }

    @Operation(
            summary = "인계 요청",
            description = "번역 작업을 다른 봉사자에게 인계합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인계 성공"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @PostMapping("/handover")
    public ResponseEntity<Map<String, Object>> handover(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId,
            @Valid @RequestBody HandoverRequest request) {

        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            userId = adminAuthUtil.getUserIdFromToken(authHeader);
        } else {
            userId = 1L; // 임시
        }

        // 락 확인
        if (!lockService.isLockedByUser(documentId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "락을 보유하고 있지 않습니다.");
        }

        // 인계 메모 저장 (락에 저장)
        var lockOpt = lockService.getLockStatus(documentId);
        if (lockOpt.isPresent()) {
            DocumentLock lock = lockOpt.get();
            lock.setHandoverMemo(request.getMemo());
            lock.setCompletedParagraphs(request.getCompletedParagraphs() != null 
                    ? request.getCompletedParagraphs().toString() 
                    : null);
            // 락 해제
            lockService.releaseLock(documentId, userId);
        }

        // 문서 상태를 PENDING_TRANSLATION으로 변경
        documentService.updateDocument(documentId,
                UpdateDocumentRequest.builder().status("PENDING_TRANSLATION").build(),
                userId);

        return ResponseEntity.ok(Map.of("success", true, "message", "인계가 완료되었습니다."));
    }

    @Operation(
            summary = "번역 완료",
            description = "번역 작업을 완료하고 검토 대기 상태로 변경합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "완료 성공"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @PostMapping("/complete")
    public ResponseEntity<Map<String, Object>> completeTranslation(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId,
            @Valid @RequestBody CompleteTranslationRequest request) {

        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            userId = adminAuthUtil.getUserIdFromToken(authHeader);
        } else {
            userId = 1L; // 임시
        }

        // 락 확인
        if (!lockService.isLockedByUser(documentId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "락을 보유하고 있지 않습니다.");
        }

        // 번역 버전 생성
        versionService.createVersion(documentId,
                CreateDocumentVersionRequest.builder()
                        .versionType("MANUAL_TRANSLATION")
                        .content(request.getContent())
                        .isFinal(false)
                        .build(),
                userId);

        // 락 해제
        lockService.releaseLock(documentId, userId);

        // 문서 상태를 PENDING_REVIEW로 변경
        documentService.updateDocument(documentId,
                UpdateDocumentRequest.builder().status("PENDING_REVIEW").build(),
                userId);

        return ResponseEntity.ok(Map.of("success", true, "message", "번역이 완료되었습니다.", "status", "PENDING_REVIEW"));
    }

    @Operation(
            summary = "임시 저장",
            description = "번역 작업 중 임시로 저장합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공")
    })
    @PutMapping("/translation")
    public ResponseEntity<Map<String, Object>> saveTranslation(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "문서 ID", required = true, example = "1")
            @PathVariable Long documentId,
            @Valid @RequestBody CompleteTranslationRequest request) {

        Long userId = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            userId = adminAuthUtil.getUserIdFromToken(authHeader);
        } else {
            userId = 1L; // 임시
        }

        // 락 확인 (선택적 - 임시 저장은 락 없이도 가능)
        // if (!lockService.isLockedByUser(documentId, userId)) {
        //     throw new ResponseStatusException(HttpStatus.FORBIDDEN, "락을 보유하고 있지 않습니다.");
        // }

        // 임시 저장 버전 생성 또는 업데이트
        // TODO: 임시 저장 버전 관리 로직 추가

        return ResponseEntity.ok(Map.of("success", true, "message", "임시 저장되었습니다."));
    }
}

