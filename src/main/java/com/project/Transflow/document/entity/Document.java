package com.project.Transflow.document.entity;

import com.project.Transflow.user.entity.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 500)
    private String originalUrl;

    @Column(nullable = false, length = 10)
    private String sourceLang; // 원문 언어 코드 (EN, KO, JA 등)

    @Column(nullable = false, length = 10)
    private String targetLang; // 번역 언어 코드

    @Column
    private Long categoryId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT"; // DRAFT, PENDING_TRANSLATION, IN_TRANSLATION, PENDING_REVIEW, APPROVED, PUBLISHED

    @Column
    private Long currentVersionId; // 현재 활성 버전 ID

    @Column
    private Integer estimatedLength; // 예상 분량 (글자 수)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy; // 생성자 (관리자)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_modified_by")
    private User lastModifiedBy; // 마지막 수정자

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

