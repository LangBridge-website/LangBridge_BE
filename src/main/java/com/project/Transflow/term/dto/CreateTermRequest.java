package com.project.Transflow.term.dto;

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
@Schema(description = "용어 사전 생성 요청")
public class CreateTermRequest {

    @Schema(description = "원문 용어", example = "Spring Boot", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "원문 용어는 필수입니다.")
    @Size(max = 255, message = "원문 용어는 255자 이하여야 합니다.")
    private String sourceTerm;

    @Schema(description = "번역 용어", example = "스프링 부트", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "번역 용어는 필수입니다.")
    @Size(max = 255, message = "번역 용어는 255자 이하여야 합니다.")
    private String targetTerm;

    @Schema(description = "원문 언어 코드", example = "EN", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"EN", "KO", "JA", "ZH"})
    @NotBlank(message = "원문 언어는 필수입니다.")
    private String sourceLang;

    @Schema(description = "번역 언어 코드", example = "KO", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"EN", "KO", "JA", "ZH"})
    @NotBlank(message = "번역 언어는 필수입니다.")
    private String targetLang;

    @Schema(description = "용어 설명", example = "Java 웹 애플리케이션 프레임워크")
    private String description;
}

