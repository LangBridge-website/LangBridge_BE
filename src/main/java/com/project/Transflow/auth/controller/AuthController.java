package com.project.Transflow.auth.controller;

import com.project.Transflow.auth.dto.LoginSuccessResponse;
import com.project.Transflow.auth.dto.UserResponse;
import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import com.project.Transflow.auth.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "인증 API", description = "로그인, 로그아웃, 사용자 정보 조회 API")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Operation(
            summary = "로그인 성공",
            description = "OAuth2 로그인 성공 후 JWT 토큰과 사용자 정보를 반환합니다. (인증 불필요)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = LoginSuccessResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (토큰 또는 이메일 누락, 사용자 없음)")
    })
    @GetMapping("/login/success")
    public ResponseEntity<Map<String, Object>> loginSuccess(
            @Parameter(description = "JWT 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
            @RequestParam(required = false) String token,
            @Parameter(description = "사용자 이메일", example = "user@example.com")
            @RequestParam(required = false) String email) {
        
        if (token == null || email == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing token or email"));
        }
        
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("token", token);
        response.put("user", Map.of(
            "id", user.getId(),
            "email", user.getEmail(),
            "name", user.getName(),
            "roleLevel", user.getRoleLevel(),
            "profileImage", user.getProfileImage() != null ? user.getProfileImage() : ""
        ));

        log.info("로그인 성공: {} (role_level: {})", email, user.getRoleLevel());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "로그인 실패",
            description = "OAuth2 로그인 실패 시 호출됩니다. (인증 불필요)"
    )
    @ApiResponse(responseCode = "400", description = "로그인 실패")
    @GetMapping("/login/failure")
    public ResponseEntity<Map<String, Object>> loginFailure() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "로그인 실패");
        return ResponseEntity.badRequest().body(response);
    }

    @Operation(
            summary = "현재 사용자 정보 조회",
            description = "JWT 토큰을 기반으로 현재 로그인한 사용자 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 토큰)"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "JWT")
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            
            if (!jwtUtil.validateToken(token)) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired token"));
            }

            Long userId = jwtUtil.extractUserId(token);
            Optional<User> userOpt = userRepository.findById(userId);

            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            User user = userOpt.get();
            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("email", user.getEmail());
            response.put("name", user.getName());
            response.put("roleLevel", user.getRoleLevel());
            response.put("profileImage", user.getProfileImage() != null ? user.getProfileImage() : "");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("토큰 검증 실패", e);
            return ResponseEntity.status(401).body(Map.of("error", "Token validation failed"));
        }
    }

    @Operation(
            summary = "로그아웃",
            description = "로그아웃합니다. (JWT는 stateless이므로 클라이언트에서 토큰 삭제만 하면 됩니다.)"
    )
    @ApiResponse(responseCode = "200", description = "로그아웃 성공")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "JWT")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        // JWT는 stateless이므로 클라이언트에서 토큰 삭제만 하면 됨
        // 필요시 블랙리스트 구현 가능
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "로그아웃 성공");
        return ResponseEntity.ok(response);
    }
}

