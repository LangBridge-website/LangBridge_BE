package com.project.Transflow.term.entity;

import com.project.Transflow.user.entity.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "term_dictionary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TermDictionary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String sourceTerm; // 원문 용어

    @Column(nullable = false, length = 255)
    private String targetTerm; // 번역 용어

    @Column(nullable = false, length = 10)
    private String sourceLang; // 원문 언어 코드

    @Column(nullable = false, length = 10)
    private String targetLang; // 번역 언어 코드

    @Column(columnDefinition = "TEXT")
    private String description; // 용어 설명

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy; // 생성자

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

