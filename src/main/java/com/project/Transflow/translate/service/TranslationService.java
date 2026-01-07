package com.project.Transflow.translate.service;

import com.project.Transflow.translate.dto.DeepLResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
public class TranslationService {

    private final WebClient webClient;
    private final String apiKey;

    public TranslationService(
            @Value("${deepl.api.url}") String apiUrl,
            @Value("${deepl.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    public String translate(String text, String targetLang, String sourceLang) {
        try {
            log.info("번역 시작 - Target: {}, 텍스트 길이: {}", targetLang, text.length());

            // DeepL API는 form data를 사용
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("text", text);
            formData.add("target_lang", targetLang.toUpperCase());
            if (sourceLang != null && !sourceLang.isEmpty()) {
                formData.add("source_lang", sourceLang.toUpperCase());
            }

            DeepLResponse response = webClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(formData)
                    .retrieve()
                    .bodyToMono(DeepLResponse.class)
                    .block();

            if (response != null && response.getTranslations() != null && !response.getTranslations().isEmpty()) {
                String translatedText = response.getTranslations().get(0).getText();
                log.info("번역 완료 - 번역된 텍스트 길이: {}", translatedText.length());
                return translatedText;
            }

            throw new RuntimeException("번역 결과가 비어있습니다.");

        } catch (Exception e) {
            log.error("번역 실패", e);
            throw new RuntimeException("번역 중 오류 발생: " + e.getMessage());
        }
    }
}