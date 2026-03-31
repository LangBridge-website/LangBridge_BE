package com.project.Transflow.inquiry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryReplyResponse {
    private Long id;
    private String content;
    private AuthorSummaryDto author;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
