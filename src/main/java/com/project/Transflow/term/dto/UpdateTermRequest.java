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

    @Schema(description = "구분(분야)", example = "과학")
    @Size(max = 100, message = "구분은 100자 이하여야 합니다.")
    private String category;

    @Schema(description = "기사제목", example = "만연해있는 과학 사기가 계속 증가하고 있다.")
    @Size(max = 500, message = "기사제목은 500자 이하여야 합니다.")
    private String articleTitle;

    @Schema(description = "출처(날짜)", example = "CEH, 2024. 2. 14")
    @Size(max = 200, message = "출처는 200자 이하여야 합니다.")
    private String articleSource;

    @Schema(description = "기사링크", example = "https://creation.kr/Science/...")
    @Size(max = 1000, message = "기사링크는 1000자 이하여야 합니다.")
    private String articleLink;

    @Schema(description = "메모")
    private String memo;
}

