package com.project.Transflow.settings.dto;

import com.project.Transflow.settings.entity.ApiKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyResponse {

    private Long id;
    private String serviceName;
    private boolean hasApiKey; // API 키 존재 여부 (실제 키 값은 반환하지 않음)
    private LocalDateTime updatedAt;
    private Long updatedBy;

    public static ApiKeyResponse from(ApiKey apiKey) {
        return ApiKeyResponse.builder()
                .id(apiKey.getId())
                .serviceName(apiKey.getServiceName())
                .hasApiKey(apiKey.getEncryptedApiKey() != null && !apiKey.getEncryptedApiKey().isEmpty())
                .updatedAt(apiKey.getUpdatedAt())
                .updatedBy(apiKey.getUpdatedBy())
                .build();
    }
}

