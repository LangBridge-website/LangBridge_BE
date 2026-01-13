package com.project.Transflow.document.entity;

import com.project.Transflow.user.entity.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_lock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false, unique = true)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locked_by", nullable = false)
    private User lockedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime lockedAt;

    @Column(columnDefinition = "TEXT")
    private String handoverMemo; // 인계 메모

    @Column(columnDefinition = "TEXT")
    private String completedParagraphs; // 완료된 문단 ID 배열 (JSON)
}

