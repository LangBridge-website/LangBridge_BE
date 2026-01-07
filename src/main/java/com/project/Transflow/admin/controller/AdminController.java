package com.project.Transflow.admin.controller;

import com.project.Transflow.admin.dto.UpdateUserRoleRequest;
import com.project.Transflow.admin.dto.UpdateUserRoleResponse;
import com.project.Transflow.admin.service.AdminService;
import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.user.entity.User;
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
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "관리자 API", description = "관리자 권한이 필요한 API (roleLevel 1, 2)")
@SecurityRequirement(name = "JWT")
public class AdminController {

    private final AdminService adminService;
    private final AdminAuthUtil adminAuthUtil;

    @Operation(
            summary = "사용자 역할 레벨 변경 (사용자 ID로)",
            description = "사용자 ID로 역할 레벨을 변경합니다. 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공",
                    content = @Content(schema = @Schema(implementation = UpdateUserRoleResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (존재하지 않는 사용자, 잘못된 roleLevel 등)"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PutMapping("/users/{userId}/role")
    public ResponseEntity<Map<String, Object>> updateUserRoleLevel(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "사용자 ID", required = true, example = "1")
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        
        // 권한 체크
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "관리자 권한이 필요합니다."));
        }

        try {
            User user = adminService.updateUserRoleLevel(userId, request.getRoleLevel());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "사용자 레벨이 변경되었습니다.");
            response.put("user", Map.of(
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "name", user.getName(),
                    "roleLevel", user.getRoleLevel()
            ));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("사용자 레벨 변경 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "사용자 레벨 변경 중 오류가 발생했습니다."));
        }
    }

    @Operation(
            summary = "사용자 역할 레벨 변경 (이메일로)",
            description = "이메일로 사용자의 역할 레벨을 변경합니다. 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공",
                    content = @Content(schema = @Schema(implementation = UpdateUserRoleResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (존재하지 않는 사용자, 잘못된 roleLevel 등)"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PutMapping("/users/email/{email}/role")
    public ResponseEntity<Map<String, Object>> updateUserRoleLevelByEmail(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "사용자 이메일", required = true, example = "user@example.com")
            @PathVariable String email,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        
        // 권한 체크
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "관리자 권한이 필요합니다."));
        }

        try {
            User user = adminService.updateUserRoleLevelByEmail(email, request.getRoleLevel());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "사용자 레벨이 변경되었습니다.");
            response.put("user", Map.of(
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "name", user.getName(),
                    "roleLevel", user.getRoleLevel()
            ));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("사용자 레벨 변경 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "사용자 레벨 변경 중 오류가 발생했습니다."));
        }
    }
}

