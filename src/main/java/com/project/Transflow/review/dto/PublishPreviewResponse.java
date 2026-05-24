package com.project.Transflow.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "creation.kr 게시 미리보기 응답")
public class PublishPreviewResponse {

    @Schema(description = "리뷰 ID", example = "1")
    private Long reviewId;

    @Schema(description = "문서 ID", example = "10")
    private Long documentId;

    @Schema(description = "문서 제목")
    private String title;

    @Schema(description = "게시될 sanitize된 HTML 본문")
    private String sanitizedHtml;

    @Schema(description = "원문 URL (링크 절대경로 변환용)")
    private String originalUrl;

    @Schema(description = "문서 카테고리 ID")
    private Long categoryId;

    @Schema(description = "리뷰 상태", example = "APPROVED")
    private String reviewStatus;

    @Schema(description = "문서 상태", example = "APPROVED")
    private String documentStatus;

    @Schema(description = "게시 상태", example = "NONE")
    private String publishStatus;

    @Schema(description = "게시된 URL")
    private String publishedUrl;

    @Schema(description = "게시 실패 사유")
    private String publishError;

    @Schema(description = "완전 번역 여부")
    private Boolean isComplete;

    @Schema(description = "게시 가능 여부")
    private boolean publishable;
}
