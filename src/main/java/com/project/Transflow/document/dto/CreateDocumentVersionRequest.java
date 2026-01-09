package com.project.Transflow.document.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "문서 버전 생성 요청")
public class CreateDocumentVersionRequest {

    @Schema(description = "버전 타입", example = "ORIGINAL", requiredMode = Schema.RequiredMode.REQUIRED, 
            allowableValues = {"ORIGINAL", "AI_DRAFT", "MANUAL_TRANSLATION", "FINAL"})
    @NotBlank(message = "버전 타입은 필수입니다.")
    private String versionType; // ORIGINAL, AI_DRAFT, MANUAL_TRANSLATION, FINAL

    @Schema(description = "내용 (HTML)", example = "<p>원문 내용...</p>", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    @Schema(description = "최종 버전 여부", example = "false")
    private Boolean isFinal;
}

