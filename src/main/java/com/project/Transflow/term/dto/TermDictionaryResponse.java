package com.project.Transflow.term.dto;

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
@Schema(description = "용어 사전 응답")
public class TermDictionaryResponse {

    @Schema(description = "용어 ID", example = "1")
    private Long id;

    @Schema(description = "원문 용어", example = "Spring Boot")
    private String sourceTerm;

    @Schema(description = "번역 용어", example = "스프링 부트")
    private String targetTerm;

    @Schema(description = "원문 언어 코드", example = "EN")
    private String sourceLang;

    @Schema(description = "번역 언어 코드", example = "KO")
    private String targetLang;

    @Schema(description = "용어 설명", example = "Java 웹 애플리케이션 프레임워크")
    private String description;

    @Schema(description = "생성자 정보")
    private CreatorInfo createdBy;

    @Schema(description = "생성일시", example = "2024-01-01T00:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정일시", example = "2024-01-01T00:00:00")
    private LocalDateTime updatedAt;

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

