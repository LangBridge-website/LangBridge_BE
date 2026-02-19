package com.project.Transflow.settings.service;

import com.project.Transflow.settings.dto.ApiKeyRequest;
import com.project.Transflow.settings.dto.ApiKeyResponse;
import com.project.Transflow.settings.entity.ApiKey;
import com.project.Transflow.settings.repository.ApiKeyRepository;
import com.project.Transflow.settings.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final EncryptionUtil encryptionUtil;

    /**
     * DeepL API 키 저장/업데이트
     */
    @Transactional
    public ApiKeyResponse saveDeepLApiKey(ApiKeyRequest request, Long userId) {
        try {
            String encryptedKey = encryptionUtil.encrypt(request.getApiKey());

            Optional<ApiKey> existingKey = apiKeyRepository.findByServiceName("DEEPL");

            ApiKey apiKey;
            if (existingKey.isPresent()) {
                // 업데이트
                apiKey = existingKey.get();
                apiKey.setEncryptedApiKey(encryptedKey);
                apiKey.setUpdatedBy(userId);
                log.info("DeepL API 키 업데이트 - userId: {}", userId);
            } else {
                // 신규 생성
                apiKey = ApiKey.builder()
                        .serviceName("DEEPL")
                        .encryptedApiKey(encryptedKey)
                        .updatedBy(userId)
                        .build();
                log.info("DeepL API 키 생성 - userId: {}", userId);
            }

            apiKey = apiKeyRepository.save(apiKey);
            return ApiKeyResponse.from(apiKey);
        } catch (Exception e) {
            log.error("DeepL API 키 저장 실패", e);
            throw new RuntimeException("API 키 암호화 또는 저장에 실패했습니다.", e);
        }
    }

    /**
     * DeepL API 키 조회 (암호화된 상태로)
     */
    @Transactional(readOnly = true)
    public ApiKeyResponse getDeepLApiKey() {
        Optional<ApiKey> apiKey = apiKeyRepository.findByServiceName("DEEPL");
        return apiKey.map(ApiKeyResponse::from)
                .orElse(ApiKeyResponse.builder()
                        .serviceName("DEEPL")
                        .hasApiKey(false)
                        .build());
    }

    /**
     * DeepL API 키 복호화하여 반환 (내부 사용용)
     */
    @Transactional(readOnly = true)
    public String getDecryptedDeepLApiKey() {
        Optional<ApiKey> apiKey = apiKeyRepository.findByServiceName("DEEPL");
        if (apiKey.isEmpty()) {
            log.warn("DeepL API 키가 DB에 존재하지 않습니다.");
            return null;
        }

        try {
            String decryptedKey = encryptionUtil.decrypt(apiKey.get().getEncryptedApiKey());
            return decryptedKey;
        } catch (Exception e) {
            log.error("DeepL API 키 복호화 실패", e);
            throw new RuntimeException("API 키 복호화에 실패했습니다.", e);
        }
    }
}

