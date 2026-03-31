package com.project.Transflow.inquiry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryBadgeCountsResponse {
    /** 관리자용: 살아있는 답변이 하나도 없는 문의 수 */
    private long adminUnansweredCount;
    /**
     * 일반 회원용(내 글 제외): 확인이 필요한 문의 건수(스레드) 합계.
     * = userUnreadInquiryBeforeOpenCount + userUnreadInquiryAfterReadCount
     */
    private long userNewReplyCount;
    /** 남이 쓴 문의를 아직 상세 열람하지 않은 건수 */
    private long userUnreadInquiryBeforeOpenCount;
    /** 상세 열람 후, 그 이후에 새 답변이 달린 문의 건수 */
    private long userUnreadInquiryAfterReadCount;
}
