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
@Schema(description = "번역 작업 수정 요청")
public class UpdateTranslationTaskRequest {

    @Schema(description = "상태", example = "IN_PROGRESS", allowableValues = {"AVAILABLE", "IN_PROGRESS", "SUBMITTED", "ABANDONED"})
    private String status;
}

