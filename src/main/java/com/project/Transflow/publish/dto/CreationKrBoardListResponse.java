package com.project.Transflow.publish.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreationKrBoardListResponse {

    private List<CreationKrBoardOption> boards;

    /** 문서 카테고리 기준 추천 sitePath (없으면 null) */
    private String suggestedSitePath;

    /** 문서 카테고리 기준 추천 boardId (없으면 null) */
    private String suggestedBoardId;

    /** 추천 게시판 표시명 */
    private String suggestedLabel;
}
