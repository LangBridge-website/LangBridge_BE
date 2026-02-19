package com.project.Transflow.term.dto;

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
@Schema(description = "용어 사전 대량 생성 요청")
public class BatchCreateTermRequest {

    @Schema(description = "원문 언어 코드", example = "EN", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "원문 언어는 필수입니다.")
    private String sourceLang;

    @Schema(description = "번역 언어 코드", example = "KO", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "번역 언어는 필수입니다.")
    private String targetLang;

    @Schema(description = "용어 목록 (TSV 형식: 구분\\t영어\\t한국어\\t기사제목\\t출처\\t기사링크\\t메모, 각 줄에 하나의 용어)", 
            example = "과학\tAnti-science of creationists\t사이비 과학을 하는 창조과학자 (낙인)\t만연해있는 과학 사기가 계속 증가하고 있다.\tCEH, 2024. 2. 14\thttps://creation.kr/Science/...\t메모", 
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "용어 목록은 필수입니다.")
    private String termsText;
}

