package com.project.Transflow.translate.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranslationRequest {
    private String url;
    private String targetLang; // EN, KO, JA 등
    private String sourceLang; // 선택사항 (auto-detect 가능)
}
