package com.project.Transflow.publish.controller;

import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.publish.dto.CreationKrBoardListResponse;
import com.project.Transflow.publish.dto.PublishRequest;
import com.project.Transflow.publish.dto.PublishResult;
import com.project.Transflow.publish.service.CreationKrBoardCatalogService;
import com.project.Transflow.publish.service.CreationKrPublishService;
import com.project.Transflow.settings.dto.CreationKrConnectionTestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/publish/creation-kr")
@RequiredArgsConstructor
@Tag(name = "creation.kr 게시 API", description = "creation.kr 자동 게시 POC (관리자 전용)")
@SecurityRequirement(name = "JWT")
public class CreationKrPublishController {

    private final CreationKrPublishService publishService;
    private final CreationKrBoardCatalogService boardCatalogService;
    private final AdminAuthUtil adminAuthUtil;

    @Operation(
            summary = "creation.kr 게시판 목록",
            description = "게시 시 선택 가능한 creation.kr 게시판 목록을 반환합니다. categoryId를 넘기면 추천 게시판도 포함됩니다."
    )
    @GetMapping("/boards")
    public ResponseEntity<CreationKrBoardListResponse> listBoards(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Long categoryId) {

        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(boardCatalogService.listBoards(categoryId));
    }

    @Operation(
            summary = "creation.kr 로그인 테스트 (Playwright)",
            description = "DB에 저장된 계정으로 creation.kr 로그인을 시도합니다. 권한: 관리자 이상"
    )
    @PostMapping("/test-login")
    public ResponseEntity<CreationKrConnectionTestResponse> testLogin(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {

        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            PublishResult result = publishService.testConnection();
            return ResponseEntity.ok(CreationKrConnectionTestResponse.builder()
                    .success(result.isSuccess())
                    .message(result.isSuccess()
                            ? "creation.kr 로그인에 성공했습니다."
                            : result.getErrorMessage())
                    .build());
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(CreationKrConnectionTestResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("creation.kr 로그인 테스트 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CreationKrConnectionTestResponse.builder()
                            .success(false)
                            .message("로그인 테스트 중 서버 오류: " + e.getMessage())
                            .build());
        }
    }

    @Operation(
            summary = "creation.kr 게시 (POC)",
            description = "Playwright로 creation.kr 게시판에 글을 등록합니다. 권한: 관리자 이상"
    )
    @PostMapping
    public ResponseEntity<PublishResult> publish(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody PublishRequest request) {

        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            PublishResult result = publishService.publish(request);
            if (result.isSuccess()) {
                return ResponseEntity.ok(result);
            }
            return ResponseEntity.badRequest().body(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(PublishResult.failure(e.getMessage()));
        } catch (Exception e) {
            log.error("creation.kr 게시 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(PublishResult.failure("서버 오류: " + e.getMessage()));
        }
    }
}
