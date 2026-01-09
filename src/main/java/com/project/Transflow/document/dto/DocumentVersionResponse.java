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
@Schema(description = "문서 버전 응답")
public class DocumentVersionResponse {

    @Schema(description = "버전 ID", example = "1")
    private Long id;

    @Schema(description = "문서 ID", example = "1")
    private Long documentId;

    @Schema(description = "버전 번호", example = "0")
    private Integer versionNumber;

    @Schema(description = "버전 타입", example = "ORIGINAL")
    private String versionType;

    @Schema(description = "내용 (HTML)", example = "<p>원문 내용...</p>")
    private String content;

    @Schema(description = "최종 버전 여부", example = "false")
    private Boolean isFinal;

    @Schema(description = "생성자 정보")
    private CreatorInfo createdBy;

    @Schema(description = "생성일시", example = "2024-01-01T00:00:00")
    private LocalDateTime createdAt;

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
}

