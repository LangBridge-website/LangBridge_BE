package com.project.Transflow.document.dto;

import lombok.Data;

import java.util.List;

@Data
public class SourceListEnrichmentRequest {
    /** 원문 문서 ID 목록 */
    private List<Long> sourceDocumentIds;
    /** 진행률용 ORIGINAL 문단 수가 필요한 문서 ID (보통 IN_TRANSLATION 원문) */
    private List<Long> progressDocumentIds;
}
