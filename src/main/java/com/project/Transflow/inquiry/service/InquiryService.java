package com.project.Transflow.inquiry.service;

import com.project.Transflow.admin.util.AdminAuthUtil;
import com.project.Transflow.inquiry.dto.*;
import com.project.Transflow.inquiry.entity.Inquiry;
import com.project.Transflow.inquiry.entity.InquiryReply;
import com.project.Transflow.inquiry.entity.InquiryUserReadState;
import com.project.Transflow.inquiry.repository.InquiryReplyRepository;
import com.project.Transflow.inquiry.repository.InquiryRepository;
import com.project.Transflow.inquiry.repository.InquiryUserReadStateRepository;
import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final InquiryReplyRepository inquiryReplyRepository;
    private final InquiryUserReadStateRepository readStateRepository;
    private final UserRepository userRepository;
    private final AdminAuthUtil adminAuthUtil;

    @Transactional(readOnly = true)
    public Page<InquirySummaryResponse> list(Pageable pageable, Long authorIdFilter, Long viewerUserId) {
        Page<Inquiry> page = authorIdFilter != null
                ? inquiryRepository.findByAuthor_IdOrderByCreatedAtDesc(authorIdFilter, pageable)
                : inquiryRepository.findAllByOrderByCreatedAtDesc(pageable);
        return page.map(inq -> toSummary(inq, viewerUserId));
    }

    /**
     * @param recordRead true일 때만 조회자에게 "상세를 열어 읽음" 처리
     */
    @Transactional
    public InquiryDetailResponse getDetail(Long inquiryId, Long viewerUserId, boolean recordRead) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("문의를 찾을 수 없습니다."));
        boolean locked = hasActiveReply(inquiry.getId());

        List<InquiryReplyResponse> replies = inquiryReplyRepository
                .findByInquiryIdAndDeletedAtIsNullOrderByCreatedAtAsc(inquiry.getId())
                .stream()
                .map(this::toReplyResponse)
                .collect(Collectors.toList());

        InquiryDetailResponse response = InquiryDetailResponse.builder()
                .id(inquiry.getId())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .author(toAuthor(inquiry.getAuthor()))
                .createdAt(inquiry.getCreatedAt())
                .updatedAt(inquiry.getUpdatedAt())
                .locked(locked)
                .replies(replies)
                .build();

        if (recordRead && viewerUserId != null) {
            markViewerRead(inquiry, viewerUserId);
        }
        return response;
    }

    @Transactional
    public InquiryDetailResponse createInquiry(CreateInquiryRequest request, Long authorId) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Inquiry inquiry = Inquiry.builder()
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .author(author)
                .build();
        inquiry = inquiryRepository.save(inquiry);
        return getDetail(inquiry.getId(), authorId, false);
    }

    @Transactional
    public InquiryDetailResponse updateInquiry(Long inquiryId, UpdateInquiryRequest request, Long authorId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("문의를 찾을 수 없습니다."));
        if (!inquiry.getAuthor().getId().equals(authorId)) {
            throw new IllegalArgumentException("본인이 작성한 문의만 수정할 수 있습니다.");
        }
        if (hasActiveReply(inquiryId)) {
            throw new IllegalArgumentException("관리자 답변이 달린 문의는 수정할 수 없습니다.");
        }
        inquiry.setTitle(request.getTitle().trim());
        inquiry.setContent(request.getContent().trim());
        inquiryRepository.save(inquiry);
        return getDetail(inquiryId, authorId, false);
    }

    @Transactional
    public void deleteInquiry(Long inquiryId, Long authorId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("문의를 찾을 수 없습니다."));
        if (!inquiry.getAuthor().getId().equals(authorId)) {
            throw new IllegalArgumentException("본인이 작성한 문의만 삭제할 수 있습니다.");
        }
        if (hasActiveReply(inquiryId)) {
            throw new IllegalArgumentException("관리자 답변이 달린 문의는 삭제할 수 없습니다.");
        }
        // 소프트 삭제된 답변 row도 FK를 잡고 있으므로 부모 삭제 전에 물리 삭제
        inquiryReplyRepository.deleteByInquiryId(inquiryId);
        readStateRepository.deleteAll(readStateRepository.findAllByInquiryId(inquiryId));
        inquiryRepository.delete(inquiry);
    }

    @Transactional
    public InquiryReplyResponse createReply(Long inquiryId, CreateReplyRequest request, Long adminUserId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("문의를 찾을 수 없습니다."));
        User author = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        InquiryReply reply = InquiryReply.builder()
                .inquiry(inquiry)
                .author(author)
                .content(request.getContent().trim())
                .build();
        reply = inquiryReplyRepository.save(reply);
        return toReplyResponse(reply);
    }

    @Transactional
    public InquiryReplyResponse updateReply(Long inquiryId, Long replyId, UpdateReplyRequest request, Long adminUserId) {
        InquiryReply reply = inquiryReplyRepository.findById(replyId)
                .orElseThrow(() -> new IllegalArgumentException("답변을 찾을 수 없습니다."));
        if (!reply.getInquiry().getId().equals(inquiryId)) {
            throw new IllegalArgumentException("문의와 답변이 일치하지 않습니다.");
        }
        if (reply.isDeleted()) {
            throw new IllegalArgumentException("삭제된 답변입니다.");
        }
        if (!reply.getAuthor().getId().equals(adminUserId)) {
            throw new IllegalArgumentException("본인이 작성한 답변만 수정할 수 있습니다.");
        }
        reply.setContent(request.getContent().trim());
        inquiryReplyRepository.save(reply);
        return toReplyResponse(reply);
    }

    @Transactional
    public void deleteReply(Long inquiryId, Long replyId, Long adminUserId) {
        InquiryReply reply = inquiryReplyRepository.findById(replyId)
                .orElseThrow(() -> new IllegalArgumentException("답변을 찾을 수 없습니다."));
        if (!reply.getInquiry().getId().equals(inquiryId)) {
            throw new IllegalArgumentException("문의와 답변이 일치하지 않습니다.");
        }
        if (reply.isDeleted()) {
            return;
        }
        if (!reply.getAuthor().getId().equals(adminUserId)) {
            throw new IllegalArgumentException("본인이 작성한 답변만 삭제할 수 있습니다.");
        }
        reply.setDeletedAt(LocalDateTime.now());
        inquiryReplyRepository.save(reply);
    }

    @Transactional(readOnly = true)
    public InquiryBadgeCountsResponse getBadgeCounts(String authHeader) {
        Long userId = adminAuthUtil.getUserIdFromToken(authHeader);
        if (userId == null) {
            return InquiryBadgeCountsResponse.builder()
                    .adminUnansweredCount(0)
                    .userNewReplyCount(0)
                    .userUnreadInquiryBeforeOpenCount(0)
                    .userUnreadInquiryAfterReadCount(0)
                    .build();
        }
        UnreadInquirySplit split = countUnreadInquiryThreadsSplit(userId);
        long adminUnanswered = 0;
        if (adminAuthUtil.isAdminOrAbove(authHeader)) {
            adminUnanswered = inquiryRepository.countUnanswered();
        }
        return InquiryBadgeCountsResponse.builder()
                .adminUnansweredCount(adminUnanswered)
                .userNewReplyCount(split.beforeOpen + split.afterRead)
                .userUnreadInquiryBeforeOpenCount(split.beforeOpen)
                .userUnreadInquiryAfterReadCount(split.afterRead)
                .build();
    }

    /**
     * 일반 회원 기준(본인 작성 문의 제외):
     * - beforeOpen: 남이 작성한 문의를 아직 한 번도 상세 열람하지 않은 문의 건수
     * - afterRead: 상세 열람 후, 그 이후에 새 답변이 달린 문의 건수
     */
    private UnreadInquirySplit countUnreadInquiryThreadsSplit(Long viewerUserId) {
        List<Inquiry> inquiries = inquiryRepository.findAll();
        long beforeOpen = 0;
        long afterRead = 0;
        for (Inquiry inq : inquiries) {
            if (inq.getAuthor().getId().equals(viewerUserId)) {
                continue;
            }
            Optional<InquiryUserReadState> opt = readStateRepository.findByInquiryAndUserIds(inq.getId(), viewerUserId);
            long unread = opt.map(state -> inquiryReplyRepository.countByInquiryIdAndDeletedAtIsNullAndCreatedAtAfter(
                            inq.getId(), state.getLastReadAt()))
                    .orElse(0L);

            if (opt.isEmpty()) {
                beforeOpen++;
            } else if (unread > 0) {
                afterRead++;
            }
        }
        return new UnreadInquirySplit(beforeOpen, afterRead);
    }

    private static final class UnreadInquirySplit {
        final long beforeOpen;
        final long afterRead;

        UnreadInquirySplit(long beforeOpen, long afterRead) {
            this.beforeOpen = beforeOpen;
            this.afterRead = afterRead;
        }
    }

    private long countUnreadRepliesForInquiry(Long inquiryId, Long viewerUserId) {
        return readStateRepository.findByInquiryAndUserIds(inquiryId, viewerUserId)
                .map(state -> inquiryReplyRepository.countByInquiryIdAndDeletedAtIsNullAndCreatedAtAfter(
                        inquiryId, state.getLastReadAt()))
                .orElse(1L);
    }

    private void markViewerRead(Inquiry inquiry, Long viewerUserId) {
        User viewer = userRepository.findById(viewerUserId).orElse(null);
        if (viewer == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        InquiryUserReadState state = readStateRepository
                .findByInquiryAndUserIds(inquiry.getId(), viewerUserId)
                .orElse(null);
        if (state == null) {
            state = InquiryUserReadState.builder()
                    .inquiry(inquiry)
                    .user(viewer)
                    .lastReadAt(now)
                    .build();
        } else {
            state.setLastReadAt(now);
        }
        readStateRepository.save(state);
    }

    private boolean hasActiveReply(Long inquiryId) {
        return inquiryReplyRepository.countByInquiryIdAndDeletedAtIsNull(inquiryId) > 0;
    }

    private InquirySummaryResponse toSummary(Inquiry inquiry, Long viewerUserId) {
        long replyCount = inquiryReplyRepository.countByInquiryIdAndDeletedAtIsNull(inquiry.getId());
        long unreadReplyCount = 0;
        if (viewerUserId != null && !inquiry.getAuthor().getId().equals(viewerUserId)) {
            unreadReplyCount = countUnreadRepliesForInquiry(inquiry.getId(), viewerUserId);
        }
        return InquirySummaryResponse.builder()
                .id(inquiry.getId())
                .title(inquiry.getTitle())
                .author(toAuthor(inquiry.getAuthor()))
                .createdAt(inquiry.getCreatedAt())
                .hasAdminReply(replyCount > 0)
                .replyCount(replyCount)
                .unreadReplyCount(unreadReplyCount)
                .build();
    }

    private InquiryReplyResponse toReplyResponse(InquiryReply reply) {
        return InquiryReplyResponse.builder()
                .id(reply.getId())
                .content(reply.getContent())
                .author(toAuthor(reply.getAuthor()))
                .createdAt(reply.getCreatedAt())
                .updatedAt(reply.getUpdatedAt())
                .build();
    }

    private AuthorSummaryDto toAuthor(User user) {
        return AuthorSummaryDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }
}
