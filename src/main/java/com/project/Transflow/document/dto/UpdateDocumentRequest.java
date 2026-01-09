package com.project.Transflow.document.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "문서 수정 요청")
public class UpdateDocumentRequest {

    @Schema(description = "문서 제목", example = "Spring Boot 가이드 (수정)")
    @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
    private String title;

    @Schema(description = "원문 URL", example = "https://example.com/article-updated")
    @Size(max = 500, message = "URL은 500자 이하여야 합니다.")
    private String originalUrl;

    @Schema(description = "원문 언어 코드", example = "EN", allowableValues = {"EN", "KO", "JA", "ZH"})
    private String sourceLang;

    @Schema(description = "번역 언어 코드", example = "KO", allowableValues = {"EN", "KO", "JA", "ZH"})
    private String targetLang;

    @Schema(description = "카테고리 ID", example = "2")
    private Long categoryId;

    @Schema(description = "상태", example = "PENDING_TRANSLATION", allowableValues = {"DRAFT", "PENDING_TRANSLATION", "IN_TRANSLATION", "PENDING_REVIEW", "APPROVED", "PUBLISHED"})
    private String status;

    @Schema(description = "예상 분량 (글자 수)", example = "6000")
    private Integer estimatedLength;
}

