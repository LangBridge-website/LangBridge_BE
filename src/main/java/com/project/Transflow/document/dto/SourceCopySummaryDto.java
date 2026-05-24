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
public class SourceCopySummaryDto {
    private int totalCopyCount;
    private int inTranslationCount;
    @Builder.Default
    private List<String> workerNames = new ArrayList<>();
    @Builder.Default
    private List<String> copyStatuses = new ArrayList<>();
}
