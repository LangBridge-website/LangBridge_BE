package com.project.Transflow.document.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "문서 생성 요청")
public class CreateDocumentRequest {

    @Schema(description = "문서 제목", example = "Spring Boot 가이드", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
    private String title;

    @Schema(description = "원문 URL", example = "https://example.com/article", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "원문 URL은 필수입니다.")
    @Size(max = 500, message = "URL은 500자 이하여야 합니다.")
    private String originalUrl;

    @Schema(description = "원문 언어 코드", example = "EN", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"EN", "KO", "JA", "ZH"})
    @NotBlank(message = "원문 언어는 필수입니다.")
    private String sourceLang;

    @Schema(description = "번역 언어 코드", example = "KO", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"EN", "KO", "JA", "ZH"})
    @NotBlank(message = "번역 언어는 필수입니다.")
    private String targetLang;

    @Schema(description = "카테고리 ID", example = "1")
    private Long categoryId;

    @Schema(description = "예상 분량 (글자 수)", example = "5000")
    private Integer estimatedLength;

    @Schema(description = "문서 상태", example = "DRAFT", allowableValues = {"DRAFT", "PENDING_TRANSLATION", "IN_TRANSLATION", "PENDING_REVIEW", "APPROVED", "PUBLISHED"})
    private String status;
}

