package com.project.Transflow.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {
    @Builder.Default
    private List<DashboardDocumentCardDto> pendingDocuments = new ArrayList<>();
    @Builder.Default
    private List<DashboardDocumentCardDto> workingDocuments = new ArrayList<>();
    private Integer reviewPendingCount;
    private DashboardDocumentCardDto latestReviewDocument;
    @Builder.Default
    private List<DashboardDocumentCardDto> approvedDocuments = new ArrayList<>();
    @Builder.Default
    private List<DashboardDocumentCardDto> rejectedDocuments = new ArrayList<>();
}
