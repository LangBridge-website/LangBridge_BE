package com.project.Transflow.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "번역 작업 응답")
public class TranslationTaskResponse {

    @Schema(description = "작업 ID", example = "1")
    private Long id;

    @Schema(description = "문서 정보")
    private DocumentInfo document;

    @Schema(description = "번역봉사자 정보")
    private TranslatorInfo translator;

    @Schema(description = "할당한 관리자 정보")
    private AssignerInfo assignedBy;

    @Schema(description = "상태", example = "IN_PROGRESS")
    private String status;

    @Schema(description = "작업 시작 시점", example = "2024-01-01T00:00:00")
    private LocalDateTime startedAt;

    @Schema(description = "제출 시점", example = "2024-01-02T00:00:00")
    private LocalDateTime submittedAt;

    @Schema(description = "마지막 활동 시점", example = "2024-01-01T12:00:00")
    private LocalDateTime lastActivityAt;

    @Schema(description = "생성일시", example = "2024-01-01T00:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정일시", example = "2024-01-01T00:00:00")
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "문서 정보")
    public static class DocumentInfo {
        @Schema(description = "문서 ID", example = "1")
        private Long id;

        @Schema(description = "문서 제목", example = "Spring Boot 가이드")
        private String title;

        @Schema(description = "문서 상태", example = "IN_TRANSLATION")
        private String status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "번역봉사자 정보")
    public static class TranslatorInfo {
        @Schema(description = "번역봉사자 ID", example = "2")
        private Long id;

        @Schema(description = "번역봉사자 이메일", example = "translator@example.com")
        private String email;

        @Schema(description = "번역봉사자 이름", example = "번역봉사자")
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "할당자 정보")
    public static class AssignerInfo {
        @Schema(description = "할당자 ID", example = "1")
        private Long id;

        @Schema(description = "할당자 이메일", example = "admin@example.com")
        private String email;

        @Schema(description = "할당자 이름", example = "관리자")
        private String name;
    }
}

