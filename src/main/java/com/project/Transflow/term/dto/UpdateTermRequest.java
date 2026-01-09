package com.project.Transflow.term.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "용어 사전 수정 요청")
public class UpdateTermRequest {

    @Schema(description = "원문 용어", example = "Spring Boot (수정)")
    @Size(max = 255, message = "원문 용어는 255자 이하여야 합니다.")
    private String sourceTerm;

    @Schema(description = "번역 용어", example = "스프링 부트 (수정)")
    @Size(max = 255, message = "번역 용어는 255자 이하여야 합니다.")
    private String targetTerm;

    @Schema(description = "용어 설명", example = "Java 웹 애플리케이션 프레임워크 (업데이트)")
    private String description;
}

