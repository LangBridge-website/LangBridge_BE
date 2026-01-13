package com.project.Transflow.document.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "인계 요청")
public class HandoverRequest {

    @Schema(description = "완료된 문단 ID 배열", example = "[1, 2, 3]")
    private List<Integer> completedParagraphs;

    @Schema(description = "남은 작업 메모", example = "15-30번 문단 남음")
    @NotBlank(message = "남은 작업 메모는 필수입니다.")
    private String memo;

    @Schema(description = "주의 용어/표현 메모", example = "전문 용어 주의")
    private String terms;
}

