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
public class InquirySummaryResponse {
    private Long id;
    private String title;
    private AuthorSummaryDto author;
    private LocalDateTime createdAt;
    private boolean hasAdminReply;
    private long replyCount;
    /** 조회자가 글 작성자가 아닐 때: 미확인 상태면 1, 확인 후 새 답변 수 */
    private long unreadReplyCount;
}
