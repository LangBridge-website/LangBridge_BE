package com.project.Transflow.document.entity;

import com.project.Transflow.user.entity.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false)
    private Integer versionNumber; // 0: 원문, 1: AI초벌번역, 2+: 수동번역

    @Column(nullable = false, length = 20)
    private String versionType; // ORIGINAL, AI_DRAFT, MANUAL_TRANSLATION, FINAL

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content; // 원문 또는 번역된 HTML 내용

    @Column(nullable = false)
    @Builder.Default
    private Boolean isFinal = false; // 최종 버전 여부

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy; // 버전 생성자

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

