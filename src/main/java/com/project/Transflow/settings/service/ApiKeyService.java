package com.project.Transflow.settings.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.Transflow.settings.dto.ApiKeyRequest;
import com.project.Transflow.settings.dto.ApiKeyResponse;
import com.project.Transflow.settings.dto.CreationKrCredentialRequest;
import com.project.Transflow.settings.dto.CreationKrCredentialResponse;
import com.project.Transflow.settings.dto.CreationKrCredentials;
import com.project.Transflow.settings.entity.ApiKey;
import com.project.Transflow.settings.repository.ApiKeyRepository;
import com.project.Transflow.settings.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String SERVICE_DEEPL = "DEEPL";
    private static final String SERVICE_CREATION_KR = "CREATION_KR";

    private final ApiKeyRepository apiKeyRepository;
    private final EncryptionUtil encryptionUtil;
    private final ObjectMapper objectMapper;

    /**
     * DeepL API 키 저장/업데이트
     */
    @Transactional
    public ApiKeyResponse saveDeepLApiKey(ApiKeyRequest request, Long userId) {
        try {
            String encryptedKey = encryptionUtil.encrypt(request.getApiKey());

            Optional<ApiKey> existingKey = apiKeyRepository.findByServiceName(SERVICE_DEEPL);

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
                        .serviceName(SERVICE_DEEPL)
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
        Optional<ApiKey> apiKey = apiKeyRepository.findByServiceName(SERVICE_DEEPL);
        return apiKey.map(ApiKeyResponse::from)
                .orElse(ApiKeyResponse.builder()
                        .serviceName(SERVICE_DEEPL)
                        .hasApiKey(false)
                        .build());
    }

    /**
     * DeepL API 키 복호화하여 반환 (내부 사용용)
     */
    @Transactional(readOnly = true)
    public String getDecryptedDeepLApiKey() {
        Optional<ApiKey> apiKey = apiKeyRepository.findByServiceName(SERVICE_DEEPL);
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

    /**
     * creation.kr 계정 저장/업데이트 (이메일 + 비밀번호 JSON 암호화)
     */
    @Transactional
    public CreationKrCredentialResponse saveCreationKrCredentials(CreationKrCredentialRequest request, Long userId) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("email", request.getEmail().trim());
            payload.put("password", request.getPassword());
            String json = objectMapper.writeValueAsString(payload);
            String encrypted = encryptionUtil.encrypt(json);

            Optional<ApiKey> existingKey = apiKeyRepository.findByServiceName(SERVICE_CREATION_KR);

            ApiKey apiKey;
            if (existingKey.isPresent()) {
                apiKey = existingKey.get();
                apiKey.setEncryptedApiKey(encrypted);
                apiKey.setUpdatedBy(userId);
                log.info("creation.kr 계정 업데이트 - userId: {}", userId);
            } else {
                apiKey = ApiKey.builder()
                        .serviceName(SERVICE_CREATION_KR)
                        .encryptedApiKey(encrypted)
                        .updatedBy(userId)
                        .build();
                log.info("creation.kr 계정 생성 - userId: {}", userId);
            }

            apiKey = apiKeyRepository.save(apiKey);
            return toCreationKrResponse(apiKey, request.getEmail().trim());
        } catch (JsonProcessingException e) {
            log.error("creation.kr 계정 JSON 변환 실패", e);
            throw new RuntimeException("계정 정보 저장에 실패했습니다.", e);
        } catch (Exception e) {
            log.error("creation.kr 계정 저장 실패", e);
            throw new RuntimeException("계정 정보 암호화 또는 저장에 실패했습니다.", e);
        }
    }

    /**
     * creation.kr 계정 등록 여부 조회 (비밀번호 미반환, 이메일 마스킹)
     */
    @Transactional(readOnly = true)
    public CreationKrCredentialResponse getCreationKrCredentials() {
        Optional<ApiKey> apiKey = apiKeyRepository.findByServiceName(SERVICE_CREATION_KR);
        if (apiKey.isEmpty()) {
            return CreationKrCredentialResponse.builder()
                    .serviceName(SERVICE_CREATION_KR)
                    .hasCredentials(false)
                    .build();
        }
        return toCreationKrResponse(apiKey.get(), extractEmail(apiKey.get()).orElse(null));
    }

    /**
     * creation.kr 계정 복호화 (내부 사용 — Playwright 로그인 등)
     */
    @Transactional(readOnly = true)
    public CreationKrCredentials getDecryptedCreationKrCredentials() {
        Optional<ApiKey> apiKey = apiKeyRepository.findByServiceName(SERVICE_CREATION_KR);
        if (apiKey.isEmpty()) {
            log.warn("creation.kr 계정이 DB에 존재하지 않습니다.");
            return null;
        }

        try {
            String decrypted = encryptionUtil.decrypt(apiKey.get().getEncryptedApiKey());
            Map<String, String> map = objectMapper.readValue(decrypted, new TypeReference<Map<String, String>>() {});
            String email = map.get("email");
            String password = map.get("password");
            if (email == null || email.isBlank() || password == null || password.isBlank()) {
                log.warn("creation.kr 계정 정보가 불완전합니다.");
                return null;
            }
            return new CreationKrCredentials(email, password);
        } catch (Exception e) {
            log.error("creation.kr 계정 복호화 실패", e);
            throw new RuntimeException("creation.kr 계정 복호화에 실패했습니다.", e);
        }
    }

    private CreationKrCredentialResponse toCreationKrResponse(ApiKey apiKey, String plainEmail) {
        String maskedEmail = plainEmail != null ? maskEmail(plainEmail) : null;
        boolean hasCredentials = apiKey.getEncryptedApiKey() != null && !apiKey.getEncryptedApiKey().isEmpty();
        return CreationKrCredentialResponse.builder()
                .serviceName(SERVICE_CREATION_KR)
                .hasCredentials(hasCredentials)
                .email(maskedEmail)
                .updatedAt(apiKey.getUpdatedAt())
                .updatedBy(apiKey.getUpdatedBy())
                .build();
    }

    private Optional<String> extractEmail(ApiKey apiKey) {
        try {
            CreationKrCredentials creds = getDecryptedCreationKrCredentialsFromKey(apiKey);
            return creds != null ? Optional.of(creds.getEmail()) : Optional.empty();
        } catch (Exception e) {
            log.warn("creation.kr 이메일 추출 실패", e);
            return Optional.empty();
        }
    }

    private CreationKrCredentials getDecryptedCreationKrCredentialsFromKey(ApiKey apiKey) throws Exception {
        String decrypted = encryptionUtil.decrypt(apiKey.getEncryptedApiKey());
        Map<String, String> map = objectMapper.readValue(decrypted, new TypeReference<Map<String, String>>() {});
        String email = map.get("email");
        String password = map.get("password");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return null;
        }
        return new CreationKrCredentials(email, password);
    }

    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.length() <= 3) {
            return local.charAt(0) + "***" + domain;
        }
        return local.substring(0, 3) + "***" + domain;
    }
}

