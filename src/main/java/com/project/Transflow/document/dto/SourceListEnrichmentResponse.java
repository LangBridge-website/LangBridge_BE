package com.project.Transflow.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceListEnrichmentResponse {
    @Builder.Default
    private Map<Long, SourceCopySummaryDto> copySummaries = new HashMap<>();
    /** 현재 사용자가 IN_TRANSLATION 복사본을 가진 원문 ID */
    @Builder.Default
    private Set<Long> myInTranslationSourceIds = new HashSet<>();
    /** documentId → ORIGINAL HTML 기준 문단 수 (진행률 분모) */
    @Builder.Default
    private Map<Long, Integer> originalParagraphCounts = new HashMap<>();
}
