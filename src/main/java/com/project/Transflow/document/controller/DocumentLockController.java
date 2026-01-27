package com.project.Transflow.document.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.document.dto.*;
import com.project.Transflow.document.entity.DocumentLock;
import com.project.Transflow.document.repository.DocumentLockRepository;
import com.project.Transflow.document.service.DocumentLockService;
import com.project.Transflow.document.service.DocumentService;
import com.project.Transflow.document.service.DocumentVersionService;
import com.project.Transflow.document.service.HandoverHistoryService;
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
import java.util.ArrayList;
import java.util.List;
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
    private final HandoverHistoryService handoverHistoryService;
    private final AdminAuthUtil adminAuthUtil;
    private final ObjectMapper objectMapper;
    private final DocumentLockRepository lockRepository;

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
            try {
                userId = adminAuthUtil.getUserIdFromToken(authHeader);
            } catch (Exception e) {
                log.warn("토큰에서 사용자 ID 추출 실패, 기본 사용자 사용: {}", e.getMessage());
                userId = null; // null로 전달하면 서비스에서 기본 사용자 찾음
            }
        }
        // userId가 null이면 서비스에서 기본 사용자를 찾음

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

        try {
            Long userId = null;
            if (authHeader != null && !authHeader.isEmpty()) {
                try {
                    userId = adminAuthUtil.getUserIdFromToken(authHeader);
                } catch (Exception e) {
                    log.warn("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
                    userId = null; // null로 처리하여 기본 사용자 사용
                }
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
            
            // null 체크 추가
            if (lock.getLockedBy() == null) {
                log.error("락의 lockedBy가 null입니다: documentId={}", documentId);
                LockStatusResponse response = LockStatusResponse.builder()
                        .locked(false)
                        .canEdit(false)
                        .build();
                return ResponseEntity.ok(response);
            }

            // userId가 null이면 편집 불가로 처리
            boolean canEdit = userId != null && lock.getLockedBy().getId().equals(userId);

            // completedParagraphs JSON 파싱
            List<Integer> completedParagraphs = new ArrayList<>();
            if (lock.getCompletedParagraphs() != null && !lock.getCompletedParagraphs().isEmpty()) {
                try {
                    completedParagraphs = objectMapper.readValue(
                            lock.getCompletedParagraphs(),
                            new TypeReference<List<Integer>>() {}
                    );
                } catch (JsonProcessingException e) {
                    log.warn("완료된 문단 목록 JSON 파싱 실패: documentId={}", documentId, e);
                    completedParagraphs = new ArrayList<>();
                }
            }

            LockStatusResponse response = LockStatusResponse.builder()
                    .locked(true)
                    .lockedBy(LockStatusResponse.LockedByInfo.builder()
                            .id(lock.getLockedBy().getId())
                            .name(lock.getLockedBy().getName())
                            .email(lock.getLockedBy().getEmail())
                            .build())
                    .lockedAt(lock.getLockedAt())
                    .canEdit(canEdit)
                    .completedParagraphs(completedParagraphs)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("락 상태 조회 중 오류 발생: documentId={}", documentId, e);
            // 에러 발생 시 락이 없는 것으로 처리
            LockStatusResponse response = LockStatusResponse.builder()
                    .locked(false)
                    .canEdit(false)
                    .build();
            return ResponseEntity.ok(response);
        }
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
            try {
                userId = adminAuthUtil.getUserIdFromToken(authHeader);
            } catch (Exception e) {
                log.warn("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
                userId = null;
            }
        }

        lockService.releaseLock(documentId, userId);

        // 문서 상태를 PENDING_TRANSLATION으로 변경
        UpdateDocumentRequest updateRequest = new UpdateDocumentRequest();
        updateRequest.setStatus("PENDING_TRANSLATION");
        documentService.updateDocument(documentId, updateRequest, userId);

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
            try {
                userId = adminAuthUtil.getUserIdFromToken(authHeader);
            } catch (Exception e) {
                log.warn("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
                userId = null;
            }
        }

        // 락 확인 (userId가 null이면 체크하지 않음 - 개발 단계)
        if (userId != null && !lockService.isLockedByUser(documentId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "락을 보유하고 있지 않습니다.");
        }

        // 인계 히스토리 생성 (옵션 2: 별도 엔티티로 관리)
        handoverHistoryService.createHandover(documentId, request, userId);

        // 락 해제
        var lockOpt = lockService.getLockStatus(documentId);
        if (lockOpt.isPresent()) {
            lockService.releaseLock(documentId, userId);
        }

        // 문서 상태를 PENDING_TRANSLATION으로 변경
        UpdateDocumentRequest updateRequest = new UpdateDocumentRequest();
        updateRequest.setStatus("PENDING_TRANSLATION");
        documentService.updateDocument(documentId, updateRequest, userId);

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
            try {
                userId = adminAuthUtil.getUserIdFromToken(authHeader);
            } catch (Exception e) {
                log.warn("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
                userId = null;
            }
        }

        // 락 확인 (userId가 null이면 체크하지 않음 - 개발 단계)
        if (userId != null && !lockService.isLockedByUser(documentId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "락을 보유하고 있지 않습니다.");
        }

        // 번역 버전 생성
        CreateDocumentVersionRequest versionRequest = new CreateDocumentVersionRequest();
        versionRequest.setVersionType("MANUAL_TRANSLATION");
        versionRequest.setContent(request.getContent());
        versionRequest.setIsFinal(false);
        versionService.createVersion(documentId, versionRequest, userId);

        // completedParagraphs를 락에 저장 (락 해제 전에 저장)
        var lockOpt = lockService.getLockStatus(documentId);
        if (lockOpt.isPresent()) {
            DocumentLock lock = lockOpt.get();
            try {
                // completedParagraphs를 JSON 문자열로 변환하여 저장
                String completedParagraphsJson = null;
                if (request.getCompletedParagraphs() != null && !request.getCompletedParagraphs().isEmpty()) {
                    completedParagraphsJson = objectMapper.writeValueAsString(request.getCompletedParagraphs());
                }
                lock.setCompletedParagraphs(completedParagraphsJson);
                lockRepository.save(lock);
                log.info("✅ 번역 완료 시 completedParagraphs 저장: documentId={}, completedParagraphs={}", 
                        documentId, request.getCompletedParagraphs() != null ? request.getCompletedParagraphs().size() : 0);
            } catch (JsonProcessingException e) {
                log.error("completedParagraphs JSON 변환 실패: documentId={}", documentId, e);
            }
        }

        // 락 해제
        lockService.releaseLock(documentId, userId);

        // 문서 상태를 PENDING_REVIEW로 변경
        UpdateDocumentRequest updateRequest = new UpdateDocumentRequest();
        updateRequest.setStatus("PENDING_REVIEW");
        documentService.updateDocument(documentId, updateRequest, userId);

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

        // 임시 저장은 사용자 인증 없이도 가능 (개발 단계)
        // TODO: 임시 저장 버전 관리 로직 추가

        // completedParagraphs를 락에 저장
        var lockOpt = lockService.getLockStatus(documentId);
        if (lockOpt.isPresent()) {
            DocumentLock lock = lockOpt.get();
            try {
                // completedParagraphs를 JSON 문자열로 변환하여 저장
                String completedParagraphsJson = null;
                if (request.getCompletedParagraphs() != null && !request.getCompletedParagraphs().isEmpty()) {
                    completedParagraphsJson = objectMapper.writeValueAsString(request.getCompletedParagraphs());
                }
                lock.setCompletedParagraphs(completedParagraphsJson);
                lockRepository.save(lock);
                log.info("✅ 임시 저장 완료: documentId={}, completedParagraphs={}", 
                        documentId, request.getCompletedParagraphs() != null ? request.getCompletedParagraphs().size() : 0);
            } catch (JsonProcessingException e) {
                log.error("completedParagraphs JSON 변환 실패: documentId={}", documentId, e);
            }
        } else {
            log.warn("⚠️ 락이 없어서 completedParagraphs를 저장할 수 없습니다: documentId={}", documentId);
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "임시 저장되었습니다."));
    }
}

