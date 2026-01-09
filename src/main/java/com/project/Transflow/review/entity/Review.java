package com.project.Transflow.review.entity;

import com.project.Transflow.document.entity.Document;
import com.project.Transflow.document.entity.DocumentVersion;
import com.project.Transflow.user.entity.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_version_id", nullable = false)
    private DocumentVersion documentVersion; // 검토 대상 버전

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer; // 리뷰어 (관리자)

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(columnDefinition = "TEXT")
    private String comment; // 리뷰 코멘트

    @Column(columnDefinition = "JSON")
    private String checklist; // 체크리스트 JSON 문자열 (번역누락, 용어집적용, 검토자확인 등)

    @Column
    private LocalDateTime reviewedAt; // 리뷰 시점

    @Column
    private LocalDateTime finalApprovalAt; // 최종 승인 시점

    @Column
    private LocalDateTime publishedAt; // 게시 시점 (creation.kr)

    @Column(nullable = false)
    @Builder.Default
    private Boolean isComplete = false; // 문서 번역 완료 여부 (false: 부분 번역, true: 완전 번역)

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

