package com.project.Transflow.term.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "용어 사전 페이지네이션 응답")
public class TermDictionaryPageResponse {

    @Schema(description = "용어 목록")
    private List<TermDictionaryResponse> content;

    @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0")
    private int page;

    @Schema(description = "페이지 크기", example = "20")
    private int size;

    @Schema(description = "전체 요소 개수", example = "100")
    private long totalElements;

    @Schema(description = "전체 페이지 수", example = "5")
    private int totalPages;

    @Schema(description = "현재 페이지가 첫 페이지인지", example = "true")
    private boolean first;

    @Schema(description = "현재 페이지가 마지막 페이지인지", example = "false")
    private boolean last;
}

