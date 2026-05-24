package com.project.Transflow.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDocumentCardDto {
    private Long id;
    private String title;
    private Long categoryId;
    private Integer estimatedLength;
    private LocalDateTime updatedAt;
    private String translatorName;
    private String documentStatus;
    private Long approvedReviewId;
    private String publishedUrl;
    private String publishStatus;
    private String publishError;
    /** 카드 표시용 날짜 (승인/반려/수정 시점) */
    private LocalDateTime displayAt;
}
