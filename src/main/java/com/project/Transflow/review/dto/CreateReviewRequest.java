package com.project.Transflow.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "리뷰 생성 요청")
public class CreateReviewRequest {

    @Schema(description = "문서 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "문서 ID는 필수입니다.")
    private Long documentId;

    @Schema(description = "검토 대상 버전 ID", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "문서 버전 ID는 필수입니다.")
    private Long documentVersionId;

    @Schema(description = "리뷰 코멘트", example = "번역 품질이 우수합니다.")
    private String comment;

    @Schema(description = "체크리스트", example = "{\"translationComplete\": true, \"termDictionaryApplied\": true, \"reviewerConfirmed\": true}")
    private Map<String, Boolean> checklist;

    @Schema(description = "문서 번역 완료 여부 (false: 부분 번역, true: 완전 번역)", example = "false", defaultValue = "false")
    private Boolean isComplete = false;
}

