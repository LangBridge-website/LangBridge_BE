package com.project.Transflow.translate.service;

import com.project.Transflow.settings.service.ApiKeyService;
import com.project.Transflow.translate.dto.DeepLResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TranslationService {

    private final WebClient webClient;
    private final ApiKeyService apiKeyService;

    public TranslationService(
            @Value("${deepl.api.url}") String apiUrl,
            ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }

    /**
     * DeepL API 키 조회 (DB에서 복호화)
     */
    private String getApiKey() {
        try {
            String dbApiKey = apiKeyService.getDecryptedDeepLApiKey();
            if (dbApiKey != null && !dbApiKey.isEmpty()) {
                return dbApiKey;
            }
        } catch (Exception e) {
            log.error("DB에서 API 키 조회 실패: {}", e.getMessage());
        }

        throw new RuntimeException("DeepL API 키가 설정되지 않았습니다. 시스템 설정에서 API 키를 등록해주세요.");
    }

    public String translate(String text, String targetLang, String sourceLang) {
        return translate(text, targetLang, sourceLang, null);
    }

    public String translate(String text, String targetLang, String sourceLang, String glossaryId) {
        return translateWithRetry(text, targetLang, sourceLang, glossaryId, 3); // 최대 3번 재시도
    }
    
    /**
     * 여러 텍스트를 한 번에 번역 (배치 번역)
     * API 호출 횟수를 대폭 줄여서 속도 향상
     */
    public List<String> translateBatch(List<String> texts, String targetLang, String sourceLang) {
        return translateBatch(texts, targetLang, sourceLang, null);
    }

    public List<String> translateBatch(List<String> texts, String targetLang, String sourceLang, String glossaryId) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        
        return translateBatchWithRetry(texts, targetLang, sourceLang, glossaryId, 3);
    }
    
    private List<String> translateBatchWithRetry(List<String> texts, String targetLang, String sourceLang, String glossaryId, int maxRetries) {
        int retryCount = 0;
        long baseDelay = 1000;
        int validTextCount = 0; // 변수를 try 블록 밖으로 이동
        
        while (retryCount <= maxRetries) {
            try {
                // DeepL API는 여러 텍스트를 한 번에 번역 가능
                MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
                
                // 각 텍스트를 별도의 text 파라미터로 추가 (빈 텍스트 필터링)
                validTextCount = 0; // 초기화
                for (String text : texts) {
                    if (text != null && !text.trim().isEmpty()) {
                        // DeepL 무료 플랜: 최대 50,000자 제한
                        if (text.length() > 50000) {
                            log.warn("텍스트가 너무 깁니다 ({}자). 첫 50,000자만 번역합니다.", text.length());
                            text = text.substring(0, 50000);
                        }
                        formData.add("text", text);
                        validTextCount++;
                    }
                }
                
                // 유효한 텍스트가 없으면 빈 리스트 반환
                if (validTextCount == 0) {
                    log.warn("번역할 유효한 텍스트가 없습니다.");
                    return new ArrayList<>();
                }
                
                formData.add("target_lang", targetLang.toUpperCase());
                if (sourceLang != null && !sourceLang.isEmpty() && !sourceLang.equalsIgnoreCase("auto")) {
                    formData.add("source_lang", sourceLang.toUpperCase());
                }
                if (glossaryId != null && !glossaryId.isEmpty()) {
                    formData.add("glossary_id", glossaryId);
                }

                String currentApiKey = getApiKey(); // API 키 동적 조회
                DeepLResponse response = webClient.post()
                        .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + currentApiKey)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .bodyValue(formData)
                        .retrieve()
                        .onStatus(status -> status.value() == 429, clientResponse -> {
                            return clientResponse.createException();
                        })
                        .bodyToMono(DeepLResponse.class)
                        .block(Duration.ofMinutes(5));

                if (response != null && response.getTranslations() != null && !response.getTranslations().isEmpty()) {
                    List<String> translatedTexts = new ArrayList<>();
                    for (com.project.Transflow.translate.dto.DeepLResponse.Translation translation : response.getTranslations()) {
                        translatedTexts.add(translation.getText());
                    }
                    return translatedTexts;
                }

                throw new RuntimeException("번역 결과가 비어있습니다.");

            } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
                if (e.getStatusCode() != null && e.getStatusCode().value() == 429) {
                    if (retryCount < maxRetries) {
                        long delay = baseDelay * (1L << retryCount);
                        log.warn("DeepL API Rate Limit (429) - {}초 대기 후 재시도 ({}/{})",
                                delay / 1000, retryCount + 1, maxRetries);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("재시도 대기 중 인터럽트 발생", ie);
                        }
                        retryCount++;
                        continue;
                    } else {
                        log.error("DeepL API Rate Limit - 최대 재시도 횟수 초과");
                        throw new RuntimeException("DeepL API Rate Limit에 걸렸습니다. 잠시 후 다시 시도해주세요.");
                    }
                } else if (e.getStatusCode() != null && e.getStatusCode().value() == 400) {
                    // 400 Bad Request: 요청 형식 오류
                    String responseBody = e.getResponseBodyAsString();
                    log.error("DeepL API 400 Bad Request (배치) - 요청 형식 오류: {}", responseBody);
                    log.error("요청 내용 - targetLang: {}, sourceLang: {}, 텍스트 개수: {}", 
                            targetLang, sourceLang, validTextCount);
                    throw new RuntimeException("DeepL API 요청 형식 오류 (400): " + (responseBody != null ? responseBody : e.getMessage()));
                } else {
                    log.error("번역 API 호출 실패: {} - 상태 코드: {}", e.getMessage(), 
                            e.getStatusCode() != null ? e.getStatusCode().value() : "unknown");
                    throw new RuntimeException("번역 중 오류 발생: " + e.getMessage());
                }
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("429")) {
                    if (retryCount < maxRetries) {
                        long delay = baseDelay * (1L << retryCount);
                        log.warn("DeepL API Rate Limit (429) - {}초 대기 후 재시도 ({}/{})",
                                delay / 1000, retryCount + 1, maxRetries);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("재시도 대기 중 인터럽트 발생", ie);
                        }
                        retryCount++;
                        continue;
                    }
                }
                log.error("배치 번역 실패", e);
                throw new RuntimeException("배치 번역 중 오류 발생: " + e.getMessage());
            }
        }

        throw new RuntimeException("배치 번역 실패: 최대 재시도 횟수 초과");
    }
    
    private String translateWithRetry(String text, String targetLang, String sourceLang, String glossaryId, int maxRetries) {
        int retryCount = 0;
        long baseDelay = 1000; // 1초부터 시작
        
        // 빈 텍스트 체크
        if (text == null || text.trim().isEmpty()) {
            log.warn("빈 텍스트는 번역할 수 없습니다.");
            return text; // 원본 반환
        }
        
        // DeepL 무료 플랜: 최대 50,000자 제한
        String textToTranslate = text;
        if (textToTranslate.length() > 50000) {
            log.warn("텍스트가 너무 깁니다 ({}자). 첫 50,000자만 번역합니다.", textToTranslate.length());
            textToTranslate = textToTranslate.substring(0, 50000);
        }
        
        while (retryCount <= maxRetries) {
            try {

            // DeepL API는 form data를 사용
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
                formData.add("text", textToTranslate);
            formData.add("target_lang", targetLang.toUpperCase());
                if (sourceLang != null && !sourceLang.isEmpty() && !sourceLang.equalsIgnoreCase("auto")) {
                formData.add("source_lang", sourceLang.toUpperCase());
            }
            if (glossaryId != null && !glossaryId.isEmpty()) {
                formData.add("glossary_id", glossaryId);
            }

            String currentApiKey = getApiKey(); // API 키 동적 조회
            DeepLResponse response = webClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + currentApiKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(formData)
                    .retrieve()
                        .onStatus(status -> status.value() == 429, clientResponse -> {
                            // 429 에러는 재시도 가능
                            return clientResponse.createException();
                        })
                    .bodyToMono(DeepLResponse.class)
                        .block(Duration.ofMinutes(5)); // 5분 타임아웃

            if (response != null && response.getTranslations() != null && !response.getTranslations().isEmpty()) {
                String translatedText = response.getTranslations().get(0).getText();
                return translatedText;
            }

            throw new RuntimeException("번역 결과가 비어있습니다.");

            } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
                // 429 Too Many Requests 에러 처리
                if (e.getStatusCode() != null && e.getStatusCode().value() == 429) {
                    if (retryCount < maxRetries) {
                        // Exponential backoff: 1초, 2초, 4초...
                        long delay = baseDelay * (1L << retryCount);
                        log.warn("DeepL API Rate Limit (429) - {}초 대기 후 재시도 ({}/{})", 
                                delay / 1000, retryCount + 1, maxRetries);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("재시도 대기 중 인터럽트 발생", ie);
                        }
                        retryCount++;
                        continue;
                    } else {
                        log.error("DeepL API Rate Limit - 최대 재시도 횟수 초과");
                        throw new RuntimeException("DeepL API Rate Limit에 걸렸습니다. 잠시 후 다시 시도해주세요.");
                    }
                } else if (e.getStatusCode() != null && e.getStatusCode().value() == 400) {
                    // 400 Bad Request: 요청 형식 오류
                    String responseBody = e.getResponseBodyAsString();
                    log.error("DeepL API 400 Bad Request - 요청 형식 오류: {}", responseBody);
                    log.error("요청 내용 - targetLang: {}, sourceLang: {}, 텍스트 길이: {}", 
                            targetLang, sourceLang, textToTranslate.length());
                    throw new RuntimeException("DeepL API 요청 형식 오류 (400): " + (responseBody != null ? responseBody : e.getMessage()));
                } else {
                    // 다른 HTTP 에러
                    log.error("번역 API 호출 실패: {} - 상태 코드: {}", e.getMessage(), 
                            e.getStatusCode() != null ? e.getStatusCode().value() : "unknown");
                    throw new RuntimeException("번역 중 오류 발생: " + e.getMessage());
                }
        } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("429")) {
                    // 429 에러인데 WebClientResponseException이 아닌 경우
                    if (retryCount < maxRetries) {
                        long delay = baseDelay * (1L << retryCount);
                        log.warn("DeepL API Rate Limit (429) - {}초 대기 후 재시도 ({}/{})", 
                                delay / 1000, retryCount + 1, maxRetries);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("재시도 대기 중 인터럽트 발생", ie);
                        }
                        retryCount++;
                        continue;
                    }
                }
            log.error("번역 실패", e);
            throw new RuntimeException("번역 중 오류 발생: " + e.getMessage());
        }
        }
        
        throw new RuntimeException("번역 실패: 최대 재시도 횟수 초과");
    }
}