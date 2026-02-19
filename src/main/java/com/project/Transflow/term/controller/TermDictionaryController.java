package com.project.Transflow.term.controller;

import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.term.dto.BatchCreateTermRequest;
import com.project.Transflow.term.dto.CreateTermRequest;
import com.project.Transflow.term.dto.TermDictionaryPageResponse;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.nio.charset.StandardCharsets;
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
            summary = "용어 목록 조회 (페이지네이션)",
            description = "용어 사전 목록을 페이지네이션으로 조회합니다. 언어별 필터링 가능"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = TermDictionaryPageResponse.class)))
    })
    @GetMapping
    public ResponseEntity<TermDictionaryPageResponse> getAllTerms(
            @Parameter(description = "원문 언어 필터", example = "EN")
            @RequestParam(required = false) String sourceLang,
            @Parameter(description = "번역 언어 필터", example = "KO")
            @RequestParam(required = false) String targetLang,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        TermDictionaryPageResponse response = termDictionaryService.findAllPaged(
                sourceLang, targetLang, page, size);
        return ResponseEntity.ok(response);
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
            summary = "용어 대량 추가",
            description = "TSV 형식으로 여러 용어를 한 번에 추가합니다. 형식: 원문\\t번역\\t설명 (각 줄에 하나의 용어). 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "대량 추가 완료 (성공/실패 개수 포함)"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)")
    })
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> createTermsBatch(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody BatchCreateTermRequest request) {

        // 권한 체크 (관리자 이상)
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Long createdById = adminAuthUtil.getUserIdFromToken(authHeader);
        if (createdById == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            TermDictionaryService.BatchCreateTermResult result = 
                    termDictionaryService.createTermsBatch(request, createdById);
            
            Map<String, Object> response = Map.of(
                    "success", true,
                    "successCount", result.getSuccessCount(),
                    "failedCount", result.getFailedCount(),
                    "errors", result.getErrors()
            );
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
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

    @Operation(
            summary = "용어집 전체 내보내기",
            description = "전체 용어를 TSV 형식으로 내보냅니다. 형식: 구분(탭)영어(탭)한국어(탭)기사제목(탭)출처(탭)기사링크(탭)메모. 권한: 관리자 이상 (roleLevel 1, 2)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "내보내기 성공 (TSV 파일)"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)")
    })
    @GetMapping("/export")
    public ResponseEntity<String> exportTerms(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "원문 언어 필터", example = "EN")
            @RequestParam(required = false) String sourceLang,
            @Parameter(description = "번역 언어 필터", example = "KO")
            @RequestParam(required = false) String targetLang) {

        // 권한 체크 (관리자 이상)
        if (!adminAuthUtil.isAdminOrAbove(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            // 필터링된 용어 목록 조회
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

            // TSV 형식으로 변환 (헤더 포함)
            StringBuilder tsv = new StringBuilder();
            tsv.append("구분(분야)\t영어\t한국어\t기사제목\t출처(날짜)\t기사링크\t메모\n");

            for (TermDictionaryResponse term : terms) {
                tsv.append(escapeTsv(term.getCategory() != null ? term.getCategory() : ""))
                   .append("\t")
                   .append(escapeTsv(term.getSourceTerm()))
                   .append("\t")
                   .append(escapeTsv(term.getTargetTerm()))
                   .append("\t")
                   .append(escapeTsv(term.getArticleTitle() != null ? term.getArticleTitle() : ""))
                   .append("\t")
                   .append(escapeTsv(term.getArticleSource() != null ? term.getArticleSource() : ""))
                   .append("\t")
                   .append(escapeTsv(term.getArticleLink() != null ? term.getArticleLink() : ""))
                   .append("\t")
                   .append(escapeTsv(term.getMemo() != null ? term.getMemo() : ""))
                   .append("\n");
            }

            // 파일 다운로드를 위한 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDispositionFormData("attachment", "glossary_export.tsv");
            headers.setContentLength(tsv.toString().getBytes(StandardCharsets.UTF_8).length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(tsv.toString());

        } catch (Exception e) {
            log.error("용어집 내보내기 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * TSV 형식에서 특수문자 이스케이프 (탭과 개행문자 제거)
     */
    private String escapeTsv(String text) {
        if (text == null) {
            return "";
        }
        // TSV에서 탭과 개행문자는 공백으로 변환
        return text.replace("\t", " ").replace("\n", " ").replace("\r", " ");
    }
}

