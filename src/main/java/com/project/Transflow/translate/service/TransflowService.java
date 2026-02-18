package com.project.Transflow.translate.service;


import com.project.Transflow.translate.dto.HtmlTranslationRequest;
import com.project.Transflow.translate.dto.TranslationRequest;
import com.project.Transflow.translate.dto.TranslationResponse;
import com.project.Transflow.term.service.TermDictionaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransflowService {

    private final CrawlerService crawlerService;
    private final HtmlTranslationService htmlTranslationService;
    private final TermDictionaryService termDictionaryService;

    public TranslationResponse translateWebPage(TranslationRequest request) {
        try {
            log.info("웹페이지 번역 프로세스 시작 - URL: {}", request.getUrl());

            // 1. 웹페이지 크롤링 (HTML과 CSS 포함)
            Map<String, String> crawlResult = crawlerService.crawlWebPageWithStyles(request.getUrl());
            String originalHtml = crawlResult.get("html");
            String css = crawlResult.get("css");

            // 2. 번역이 필요한지 확인 (targetLang이 'NONE'이면 번역 건너뛰기)
            String translatedHtml = null; // 번역하지 않으면 null
            String translatedText = null;
            
            if (request.getTargetLang() != null && !request.getTargetLang().equalsIgnoreCase("NONE")) {
                // 용어집 자동 조회 (요청에 glossaryId가 없으면 자동으로 조회)
                String glossaryId = request.getGlossaryId();
                if (glossaryId == null || glossaryId.isEmpty()) {
                    try {
                        glossaryId = termDictionaryService.getGlossaryIdByLanguages(
                                request.getSourceLang(), request.getTargetLang());
                        if (glossaryId != null) {
                            log.info("용어집 자동 조회 완료: glossaryId={} ({} -> {})", 
                                    glossaryId, request.getSourceLang(), request.getTargetLang());
                        }
                    } catch (Exception e) {
                        log.warn("용어집 자동 조회 실패: {}", e.getMessage());
                        // 용어집 조회 실패해도 번역은 계속 진행
                    }
                }
                
                // HTML 구조 유지하며 번역
                translatedHtml = htmlTranslationService.translateHtml(
                        originalHtml,
                        request.getTargetLang(),
                        request.getSourceLang(),
                        glossaryId
                );
                log.info("HTML 번역 완료");
                
                // 번역된 텍스트 추출
                Document translatedDoc = Jsoup.parse(translatedHtml);
                translatedDoc.select("script, style").remove();
                translatedText = translatedDoc.body().text();
            } else {
                log.info("번역 건너뛰기 (원본 HTML만 반환)");
            }

            // 3. 원본 텍스트 추출 (하위 호환성을 위해)
            Document originalDoc = Jsoup.parse(originalHtml);
            originalDoc.select("script, style").remove();
            String originalText = originalDoc.body().text();

            // 4. 결과 반환
            return TranslationResponse.builder()
                    .originalUrl(request.getUrl())
                    // HTML 결과
                    .originalHtml(originalHtml)
                    .translatedHtml(translatedHtml) // 번역하지 않으면 null
                    .css(css)
                    // 텍스트 결과 (하위 호환성)
                    .originalText(originalText)
                    .translatedText(translatedText) // 번역하지 않으면 null
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
    
    /**
     * HTML 문자열을 직접 번역 (URL 크롤링 없이)
     * 선택된 영역만 번역할 때 사용
     */
    public TranslationResponse translateHtmlDirectly(HtmlTranslationRequest request) {
        try {
            log.info("HTML 직접 번역 시작 - HTML 길이: {}", request.getHtml().length());
            
            // 용어집 자동 조회 (요청에 glossaryId가 없으면 자동으로 조회)
            String glossaryId = request.getGlossaryId();
            if (glossaryId == null || glossaryId.isEmpty()) {
                try {
                    glossaryId = termDictionaryService.getGlossaryIdByLanguages(
                            request.getSourceLang(), request.getTargetLang());
                    if (glossaryId != null) {
                        log.info("용어집 자동 조회 완료: glossaryId={} ({} -> {})", 
                                glossaryId, request.getSourceLang(), request.getTargetLang());
                    }
                } catch (Exception e) {
                    log.warn("용어집 자동 조회 실패: {}", e.getMessage());
                    // 용어집 조회 실패해도 번역은 계속 진행
                }
            }
            
            // HTML 번역
            String translatedHtml = htmlTranslationService.translateHtml(
                    request.getHtml(),
                    request.getTargetLang(),
                    request.getSourceLang(),
                    glossaryId
            );
            
            // 텍스트 추출 (하위 호환성)
            Document originalDoc = Jsoup.parse(request.getHtml());
            originalDoc.select("script, style").remove();
            String originalText = originalDoc.body().text();
            
            Document translatedDoc = Jsoup.parse(translatedHtml);
            translatedDoc.select("script, style").remove();
            String translatedText = translatedDoc.body().text();
            
            return TranslationResponse.builder()
                    .originalUrl("direct-html")
                    .originalHtml(request.getHtml())
                    .translatedHtml(translatedHtml)
                    .originalText(originalText)
                    .translatedText(translatedText)
                    .targetLang(request.getTargetLang())
                    .sourceLang(request.getSourceLang())
                    .success(true)
                    .build();
                    
        } catch (Exception e) {
            log.error("HTML 직접 번역 실패", e);
            return TranslationResponse.builder()
                    .originalUrl("direct-html")
                    .success(false)
                    .errorMessage("HTML 번역 중 오류가 발생했습니다: " + e.getMessage())
                    .build();
        }
    }
}