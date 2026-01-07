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
    private String originalText;
    private String translatedText;
    private String sourceLang;
    private String targetLang;
    private boolean success;
    private String errorMessage;
}
