package com.project.Transflow.task.entity;

import com.project.Transflow.document.entity.Document;
import com.project.Transflow.user.entity.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "translation_task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranslationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "translator_id", nullable = false)
    private User translator; // 번역봉사자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedBy; // 할당한 관리자 (NULL: 자발적 참여)

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "AVAILABLE"; // AVAILABLE, IN_PROGRESS, SUBMITTED, ABANDONED

    @Column
    private LocalDateTime startedAt; // 작업 시작 시점

    @Column
    private LocalDateTime submittedAt; // 제출 시점

    @Column
    private LocalDateTime lastActivityAt; // 마지막 활동 시점 (중복 작업 방지용)

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

