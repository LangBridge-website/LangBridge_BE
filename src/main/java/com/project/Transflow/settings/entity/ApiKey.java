package com.project.Transflow.settings.entity;

import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "api_key")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String serviceName; // 서비스 이름 (예: "DEEPL")

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedApiKey; // 암호화된 API 키

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private Long updatedBy; // 마지막 수정한 사용자 ID
}

