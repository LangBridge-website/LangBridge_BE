package com.project.Transflow.publish.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreationKrBoardOption {

    /** creation.kr URL 경로 (예: EvidenceofFlood) */
    private String sitePath;

    /** creation.kr board ID */
    private String boardId;

    /** UI 표시명 (예: 대홍수-증거) */
    private String label;

    /** 대분류 (예: 대홍수) */
    private String majorCategory;

    /** CATEGORY | CONFIG */
    private String source;
}
