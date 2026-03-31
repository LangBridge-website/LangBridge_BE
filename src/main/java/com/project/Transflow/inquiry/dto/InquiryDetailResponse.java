package com.project.Transflow.inquiry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryDetailResponse {
    private Long id;
    private String title;
    private String content;
    private AuthorSummaryDto author;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean locked;
    private List<InquiryReplyResponse> replies;
}
