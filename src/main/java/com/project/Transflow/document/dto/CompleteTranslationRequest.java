package com.project.Transflow.document.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "번역 완료 요청")
public class CompleteTranslationRequest {

    @Schema(description = "번역된 HTML 내용", example = "<p>번역된 내용...</p>")
    @NotBlank(message = "번역 내용은 필수입니다.")
    private String content;

    @Schema(description = "완료된 문단 ID 배열", example = "[1, 2, 3, ...]")
    private List<Integer> completedParagraphs;
}

