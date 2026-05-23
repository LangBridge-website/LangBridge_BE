package com.project.Transflow.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreationKrCredentialResponse {

    private String serviceName;
    private boolean hasCredentials;
    /** 마스킹된 이메일 (예: pub***@example.com) */
    private String email;
    private LocalDateTime updatedAt;
    private Long updatedBy;
}
