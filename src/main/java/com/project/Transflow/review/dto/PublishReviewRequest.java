package com.project.Transflow.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "creation.kr 게시 요청 (게시판 선택)")
public class PublishReviewRequest {

    @Schema(description = "creation.kr 사이트 경로 (예: EvidenceofFlood)", example = "EvidenceofFlood")
    private String sitePath;

    @Schema(description = "creation.kr board ID", example = "b201810315bd97ecb8e054")
    private String boardId;
}
