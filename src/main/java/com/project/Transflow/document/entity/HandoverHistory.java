package com.project.Transflow.document.entity;

import com.project.Transflow.user.entity.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "handover_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HandoverHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handed_over_by", nullable = false)
    private User handedOverBy; // 인계한 사용자

    @Column(columnDefinition = "TEXT", nullable = false)
    private String memo; // 인계 메모

    @Column(columnDefinition = "TEXT")
    private String terms; // 주의 용어/표현 메모

    @Column(columnDefinition = "TEXT")
    private String completedParagraphs; // 완료된 문단 ID 배열 (JSON 문자열)

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

