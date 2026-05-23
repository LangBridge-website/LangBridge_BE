package com.project.Transflow.category.entity;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "category")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 엑셀의 첫 컬럼(카테고리 코드). 기존 데이터 호환을 위해 nullable=true.
    @Column(length = 100)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** creation.kr 게시판 경로 (예: EvidenceofFlood, LIfe) */
    @Column(length = 100)
    private String creationKrSitePath;

    /** creation.kr board ID (예: b201810315bd97ecb8e054) */
    @Column(length = 100)
    private String creationKrBoardId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

