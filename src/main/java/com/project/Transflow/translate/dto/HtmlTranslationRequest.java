package com.project.Transflow.translate.dto;

import lombok.Data;

@Data
public class HtmlTranslationRequest {
    private String html;
    private String targetLang;
    private String sourceLang;
    private String glossaryId; // DeepL Glossary ID (선택사항)
}



