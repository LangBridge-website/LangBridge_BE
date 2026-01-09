package com.project.Transflow.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "번역 작업 생성 요청")
public class CreateTranslationTaskRequest {

    @Schema(description = "문서 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long documentId;

    @Schema(description = "번역봉사자 ID (자발적 참여 시 현재 사용자 ID 사용)", example = "2")
    private Long translatorId;

    @Schema(description = "관리자 할당 여부 (true: 관리자가 할당, false: 자발적 참여)", example = "false")
    private Boolean isAssigned;
}

