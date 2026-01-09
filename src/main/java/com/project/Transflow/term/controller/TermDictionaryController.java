package com.project.Transflow.term.controller;

import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.term.dto.CreateTermRequest;
import com.project.Transflow.term.dto.TermDictionaryResponse;
import com.project.Transflow.term.dto.UpdateTermRequest;
import com.project.Transflow.term.service.TermDictionaryService;
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
@RequestMapping("/api/terms")
@RequiredArgsConstructor
@Tag(name = "용어 사전 API", description = "용어 사전 관리 API")
@SecurityRequirement(name = "JWT")
public class TermDictionaryController {

    private final TermDictionaryService termDictionaryService;
    private final AdminAuthUtil adminAuthUtil;

    @Operation(
            summary = "용어 추가",
            description = "용어 사전에 새 용어를 추가합니다. 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "용어 추가 성공",
                    content = @Content(schema = @Schema(implementation = TermDictionaryResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (중복된 용어 등)"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)")
    })
    @PostMapping
    public ResponseEntity<TermDictionaryResponse> createTerm(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreateTermRequest request) {

        // 권한 체크 (관리자 이상)
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Long createdById = adminAuthUtil.getUserIdFromToken(authHeader);
        if (createdById == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            TermDictionaryResponse response = termDictionaryService.createTerm(request, createdById);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
            summary = "용어 목록 조회",
            description = "용어 사전 목록을 조회합니다. 언어별 필터링 가능"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public ResponseEntity<List<TermDictionaryResponse>> getAllTerms(
            @Parameter(description = "원문 언어 필터", example = "EN")
            @RequestParam(required = false) String sourceLang,
            @Parameter(description = "번역 언어 필터", example = "KO")
            @RequestParam(required = false) String targetLang) {

        List<TermDictionaryResponse> terms;

        if (sourceLang != null && targetLang != null) {
            terms = termDictionaryService.findByLanguages(sourceLang, targetLang);
        } else if (sourceLang != null) {
            terms = termDictionaryService.findBySourceLang(sourceLang);
        } else if (targetLang != null) {
            terms = termDictionaryService.findByTargetLang(targetLang);
        } else {
            terms = termDictionaryService.findAll();
        }

        return ResponseEntity.ok(terms);
    }

    @Operation(
            summary = "용어 상세 조회",
            description = "용어 ID로 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = TermDictionaryResponse.class))),
            @ApiResponse(responseCode = "404", description = "용어를 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TermDictionaryResponse> getTermById(
            @Parameter(description = "용어 ID", required = true, example = "1")
            @PathVariable Long id) {

        return termDictionaryService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "원문 용어로 조회",
            description = "원문 용어와 언어 쌍으로 용어를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = TermDictionaryResponse.class))),
            @ApiResponse(responseCode = "404", description = "용어를 찾을 수 없음")
    })
    @GetMapping("/search")
    public ResponseEntity<TermDictionaryResponse> searchTerm(
            @Parameter(description = "원문 용어", required = true, example = "Spring Boot")
            @RequestParam String sourceTerm,
            @Parameter(description = "원문 언어", required = true, example = "EN")
            @RequestParam String sourceLang,
            @Parameter(description = "번역 언어", required = true, example = "KO")
            @RequestParam String targetLang) {

        return termDictionaryService.findBySourceTerm(sourceTerm, sourceLang, targetLang)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "용어 수정",
            description = "용어 정보를 수정합니다. 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = TermDictionaryResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (중복된 용어 등)"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @ApiResponse(responseCode = "404", description = "용어를 찾을 수 없음")
    })
    @PutMapping("/{id}")
    public ResponseEntity<TermDictionaryResponse> updateTerm(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "용어 ID", required = true, example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateTermRequest request) {

        // 권한 체크 (관리자 이상)
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            TermDictionaryResponse response = termDictionaryService.updateTerm(id, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
            summary = "용어 삭제",
            description = "용어를 삭제합니다. 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @ApiResponse(responseCode = "404", description = "용어를 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTerm(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "용어 ID", required = true, example = "1")
            @PathVariable Long id) {

        // 권한 체크 (관리자 이상)
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            termDictionaryService.deleteTerm(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "용어가 삭제되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

