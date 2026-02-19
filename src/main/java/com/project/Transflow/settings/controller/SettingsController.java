package com.project.Transflow.settings.controller;

import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.settings.dto.ApiKeyRequest;
import com.project.Transflow.settings.dto.ApiKeyResponse;
import com.project.Transflow.settings.service.ApiKeyService;
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

@Slf4j
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "시스템 설정 API", description = "시스템 설정 관리 API (관리자 전용)")
@SecurityRequirement(name = "JWT")
public class SettingsController {

    private final ApiKeyService apiKeyService;
    private final AdminAuthUtil adminAuthUtil;

    @Operation(
            summary = "DeepL API 키 저장/업데이트",
            description = "DeepL API 키를 암호화하여 DB에 저장하거나 업데이트합니다. 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "API 키 저장 성공",
                    content = @Content(schema = @Schema(implementation = ApiKeyResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @ApiResponse(responseCode = "500", description = "서버 오류 (암호화 실패 등)")
    })
    @PostMapping("/deepl-key")
    public ResponseEntity<ApiKeyResponse> saveDeepLApiKey(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody ApiKeyRequest request) {

        // 권한 체크 (관리자 이상)
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Long userId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ApiKeyResponse response = apiKeyService.saveDeepLApiKey(request, userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("DeepL API 키 저장 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(
            summary = "DeepL API 키 조회",
            description = "DeepL API 키 존재 여부를 조회합니다. 실제 키 값은 반환하지 않습니다. 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiKeyResponse.class))),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)")
    })
    @GetMapping("/deepl-key")
    public ResponseEntity<ApiKeyResponse> getDeepLApiKey(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {

        // 권한 체크 (관리자 이상)
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ApiKeyResponse response = apiKeyService.getDeepLApiKey();
        return ResponseEntity.ok(response);
    }
}

