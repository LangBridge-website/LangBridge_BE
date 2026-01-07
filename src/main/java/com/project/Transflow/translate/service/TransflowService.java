package com.project.Transflow.translate.service;


import com.project.Transflow.translate.dto.TranslationRequest;
import com.project.Transflow.translate.dto.TranslationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransflowService {

    private final CrawlerService crawlerService;
    private final TranslationService translationService;

    public TranslationResponse translateWebPage(TranslationRequest request) {
        try {
            log.info("웹페이지 번역 프로세스 시작 - URL: {}", request.getUrl());

            // 1. 웹페이지 크롤링
            String originalText = crawlerService.crawlWebPage(request.getUrl());

            // 2. 번역
            String translatedText = translationService.translate(
                    originalText,
                    request.getTargetLang(),
                    request.getSourceLang()
            );

            // 3. 결과 반환
            return TranslationResponse.builder()
                    .originalUrl(request.getUrl())
                    .originalText(originalText)
                    .translatedText(translatedText)
                    .sourceLang(request.getSourceLang())
                    .targetLang(request.getTargetLang())
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("웹페이지 번역 실패", e);
            return TranslationResponse.builder()
                    .originalUrl(request.getUrl())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
}