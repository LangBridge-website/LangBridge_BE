package com.project.Transflow.document.dto;

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
@Schema(description = "문서 응답")
public class DocumentResponse {

    @Schema(description = "문서 ID", example = "1")
    private Long id;

    @Schema(description = "문서 제목", example = "Spring Boot 가이드")
    private String title;

    @Schema(description = "원문 URL", example = "https://example.com/article")
    private String originalUrl;

    @Schema(description = "원문 언어 코드", example = "EN")
    private String sourceLang;

    @Schema(description = "번역 언어 코드", example = "KO")
    private String targetLang;

    @Schema(description = "카테고리 ID", example = "1")
    private Long categoryId;

    @Schema(description = "상태", example = "PENDING_TRANSLATION")
    private String status;

    @Schema(description = "현재 활성 버전 ID", example = "1")
    private Long currentVersionId;

    @Schema(description = "예상 분량", example = "5000")
    private Integer estimatedLength;

    @Schema(description = "생성자 정보")
    private CreatorInfo createdBy;

    @Schema(description = "마지막 수정자 정보")
    private ModifierInfo lastModifiedBy;

    @Schema(description = "생성일시", example = "2024-01-01T00:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정일시", example = "2024-01-01T00:00:00")
    private LocalDateTime updatedAt;

    @Schema(description = "최신 인계 정보")
    private HandoverInfo latestHandover;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "생성자 정보")
    public static class CreatorInfo {
        @Schema(description = "생성자 ID", example = "1")
        private Long id;

        @Schema(description = "생성자 이메일", example = "admin@example.com")
        private String email;

        @Schema(description = "생성자 이름", example = "관리자")
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "수정자 정보")
    public static class ModifierInfo {
        @Schema(description = "수정자 ID", example = "2")
        private Long id;

        @Schema(description = "수정자 이메일", example = "user@example.com")
        private String email;

        @Schema(description = "수정자 이름", example = "사용자")
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "인계 정보")
    public static class HandoverInfo {
        @Schema(description = "인계 메모", example = "15-30번 문단 남음")
        private String memo;

        @Schema(description = "주의 용어/표현 메모", example = "전문 용어 주의")
        private String terms;

        @Schema(description = "완료된 문단 ID 배열", example = "[1, 2, 3]")
        private java.util.List<Integer> completedParagraphs;

        @Schema(description = "인계한 사용자 정보")
        private CreatorInfo handedOverBy;

        @Schema(description = "인계 일시", example = "2024-01-01T00:00:00")
        private LocalDateTime handedOverAt;
    }
}

