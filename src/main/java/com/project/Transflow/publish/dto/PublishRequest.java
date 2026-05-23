package com.project.Transflow.publish.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublishRequest {

    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    @NotBlank(message = "본문 HTML은 필수입니다.")
    private String htmlContent;

    /** creation.kr 사이트 경로 (예: EvidenceofFlood, LIfe, Ape) */
    @NotBlank(message = "sitePath는 필수입니다.")
    private String sitePath;

    /** board ID — 없으면 boardMappings에서 sitePath로 조회 */
    private String boardId;

    /** 원문 URL — 이미지/링크 절대경로 변환용 */
    private String originalUrl;
}
