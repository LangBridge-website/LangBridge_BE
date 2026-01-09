package com.project.Transflow.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "리뷰 수정 요청")
public class UpdateReviewRequest {

    @Schema(description = "리뷰 코멘트", example = "번역 품질이 우수합니다. (수정)")
    private String comment;

    @Schema(description = "체크리스트", example = "{\"translationComplete\": true, \"termDictionaryApplied\": true, \"reviewerConfirmed\": true}")
    private Map<String, Boolean> checklist;

    @Schema(description = "문서 번역 완료 여부 (false: 부분 번역, true: 완전 번역)", example = "false")
    private Boolean isComplete;
}

