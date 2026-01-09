package com.project.Transflow.translate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslationResponse {
    private String originalUrl;
    
    // 텍스트 번역 결과 (기존 필드 - 하위 호환성 유지)
    private String originalText;
    private String translatedText;
    
    // HTML 번역 결과 (새 필드)
    private String originalHtml;
    private String translatedHtml;
    private String css; // CSS 스타일시트
    
    private String sourceLang;
    private String targetLang;
    private boolean success;
    private String errorMessage;
}
