package com.project.Transflow.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "리뷰 응답")
public class ReviewResponse {

    @Schema(description = "리뷰 ID", example = "1")
    private Long id;

    @Schema(description = "문서 정보")
    private DocumentInfo document;

    @Schema(description = "검토 대상 버전 정보")
    private VersionInfo documentVersion;

    @Schema(description = "리뷰어 정보")
    private ReviewerInfo reviewer;

    @Schema(description = "상태", example = "APPROVED")
    private String status;

    @Schema(description = "리뷰 코멘트", example = "번역 품질이 우수합니다.")
    private String comment;

    @Schema(description = "체크리스트", example = "{\"translationComplete\": true, \"termDictionaryApplied\": true}")
    private Map<String, Boolean> checklist;

    @Schema(description = "리뷰 시점", example = "2024-01-01T00:00:00")
    private LocalDateTime reviewedAt;

    @Schema(description = "최종 승인 시점", example = "2024-01-01T00:00:00")
    private LocalDateTime finalApprovalAt;

    @Schema(description = "게시 시점", example = "2024-01-01T00:00:00")
    private LocalDateTime publishedAt;

    @Schema(description = "문서 번역 완료 여부", example = "false")
    private Boolean isComplete;

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
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "버전 정보")
    public static class VersionInfo {
        @Schema(description = "버전 ID", example = "2")
        private Long id;

        @Schema(description = "버전 번호", example = "2")
        private Integer versionNumber;

        @Schema(description = "버전 타입", example = "MANUAL_TRANSLATION")
        private String versionType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "리뷰어 정보")
    public static class ReviewerInfo {
        @Schema(description = "리뷰어 ID", example = "1")
        private Long id;

        @Schema(description = "리뷰어 이메일", example = "admin@example.com")
        private String email;

        @Schema(description = "리뷰어 이름", example = "관리자")
        private String name;
    }
}

