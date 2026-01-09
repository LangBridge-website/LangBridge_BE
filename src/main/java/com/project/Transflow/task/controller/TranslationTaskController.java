package com.project.Transflow.task.controller;

import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.task.dto.CreateTranslationTaskRequest;
import com.project.Transflow.task.dto.TranslationTaskResponse;
import com.project.Transflow.task.service.TranslationTaskService;
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
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Tag(name = "번역 작업 API", description = "번역 작업 관리 API")
@SecurityRequirement(name = "JWT")
public class TranslationTaskController {

    private final TranslationTaskService translationTaskService;
    private final AdminAuthUtil adminAuthUtil;

    @Operation(
            summary = "번역 작업 생성",
            description = "새로운 번역 작업을 생성합니다. 자발적 참여 또는 관리자 할당"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "작업 생성 성공",
                    content = @Content(schema = @Schema(implementation = TranslationTaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (이미 존재하는 작업 등)"),
            @ApiResponse(responseCode = "404", description = "문서 또는 사용자를 찾을 수 없음")
    })
    @PostMapping
    public ResponseEntity<TranslationTaskResponse> createTask(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreateTranslationTaskRequest request) {

        Long currentUserId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long assignedById = null;
        if (request.getIsAssigned() != null && request.getIsAssigned()) {
            // 관리자 권한 체크
            if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            assignedById = currentUserId;
        }

        try {
            TranslationTaskResponse response = translationTaskService.createTask(request, currentUserId, assignedById);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
            summary = "번역 작업 시작",
            description = "번역 작업을 시작합니다. (AVAILABLE → IN_PROGRESS)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "작업 시작 성공",
                    content = @Content(schema = @Schema(implementation = TranslationTaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (권한 없음, 상태 오류 등)"),
            @ApiResponse(responseCode = "404", description = "작업을 찾을 수 없음")
    })
    @PostMapping("/{id}/start")
    public ResponseEntity<TranslationTaskResponse> startTask(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "작업 ID", required = true, example = "1")
            @PathVariable Long id) {

        Long translatorId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (translatorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            TranslationTaskResponse response = translationTaskService.startTask(id, translatorId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
            summary = "번역 작업 제출",
            description = "번역 작업을 제출합니다. (IN_PROGRESS → SUBMITTED)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "작업 제출 성공",
                    content = @Content(schema = @Schema(implementation = TranslationTaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (권한 없음, 상태 오류 등)"),
            @ApiResponse(responseCode = "404", description = "작업을 찾을 수 없음")
    })
    @PostMapping("/{id}/submit")
    public ResponseEntity<TranslationTaskResponse> submitTask(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "작업 ID", required = true, example = "1")
            @PathVariable Long id) {

        Long translatorId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (translatorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            TranslationTaskResponse response = translationTaskService.submitTask(id, translatorId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
            summary = "번역 작업 포기",
            description = "번역 작업을 포기합니다. (IN_PROGRESS → ABANDONED)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "작업 포기 성공",
                    content = @Content(schema = @Schema(implementation = TranslationTaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (권한 없음 등)"),
            @ApiResponse(responseCode = "404", description = "작업을 찾을 수 없음")
    })
    @PostMapping("/{id}/abandon")
    public ResponseEntity<TranslationTaskResponse> abandonTask(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "작업 ID", required = true, example = "1")
            @PathVariable Long id) {

        Long translatorId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (translatorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            TranslationTaskResponse response = translationTaskService.abandonTask(id, translatorId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
            summary = "번역 작업 목록 조회",
            description = "번역 작업 목록을 조회합니다. 필터링 가능"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public ResponseEntity<List<TranslationTaskResponse>> getAllTasks(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "번역봉사자 ID 필터", example = "2")
            @RequestParam(required = false) Long translatorId,
            @Parameter(description = "문서 ID 필터", example = "1")
            @RequestParam(required = false) Long documentId,
            @Parameter(description = "상태 필터", example = "IN_PROGRESS")
            @RequestParam(required = false) String status) {

        List<TranslationTaskResponse> tasks;

        if (translatorId != null && status != null) {
            tasks = translationTaskService.findByTranslatorIdAndStatus(translatorId, status);
        } else if (translatorId != null) {
            tasks = translationTaskService.findByTranslatorId(translatorId);
        } else if (documentId != null) {
            tasks = translationTaskService.findByDocumentId(documentId);
        } else if (status != null) {
            tasks = translationTaskService.findByStatus(status);
        } else {
            tasks = translationTaskService.findAll();
        }

        return ResponseEntity.ok(tasks);
    }

    @Operation(
            summary = "내 번역 작업 목록 조회",
            description = "현재 로그인한 사용자의 번역 작업 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/my-tasks")
    public ResponseEntity<List<TranslationTaskResponse>> getMyTasks(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "상태 필터", example = "IN_PROGRESS")
            @RequestParam(required = false) String status) {

        Long translatorId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (translatorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<TranslationTaskResponse> tasks;
        if (status != null) {
            tasks = translationTaskService.findByTranslatorIdAndStatus(translatorId, status);
        } else {
            tasks = translationTaskService.findByTranslatorId(translatorId);
        }

        return ResponseEntity.ok(tasks);
    }

    @Operation(
            summary = "번역 작업 상세 조회",
            description = "번역 작업 ID로 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = TranslationTaskResponse.class))),
            @ApiResponse(responseCode = "404", description = "작업을 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TranslationTaskResponse> getTaskById(
            @Parameter(description = "작업 ID", required = true, example = "1")
            @PathVariable Long id) {

        return translationTaskService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "마지막 활동 시점 업데이트",
            description = "번역 작업의 마지막 활동 시점을 업데이트합니다. (중복 작업 방지용)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업데이트 성공",
                    content = @Content(schema = @Schema(implementation = TranslationTaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (권한 없음 등)"),
            @ApiResponse(responseCode = "404", description = "작업을 찾을 수 없음")
    })
    @PutMapping("/{id}/activity")
    public ResponseEntity<TranslationTaskResponse> updateLastActivity(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "작업 ID", required = true, example = "1")
            @PathVariable Long id) {

        Long translatorId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (translatorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            TranslationTaskResponse response = translationTaskService.updateLastActivity(id, translatorId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}

