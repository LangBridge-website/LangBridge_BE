package com.project.Transflow.translate.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class KoreanStylePostProcessor {

    private static final Pattern KOREAN_CHAR_PATTERN = Pattern.compile(".*[가-힣].*");

    /**
     * 한국어 번역 결과를 평어체(이다체) 중심으로 보정합니다.
     * 과도한 변환을 막기 위해 문장 종결 표현 위주로 최소 치환만 수행합니다.
     */
    public String toPlainStyle(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (!KOREAN_CHAR_PATTERN.matcher(text).matches()) {
            return text;
        }

        String result = text;

        // 치환 순서가 중요하므로 LinkedHashMap 사용
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("였습니다.", "였다.");
        replacements.put("였습니다", "였다");
        replacements.put("입니다.", "이다.");
        replacements.put("입니다", "이다");
        replacements.put("합니다.", "한다.");
        replacements.put("합니다", "한다");
        replacements.put("했습니다.", "했다.");
        replacements.put("했습니다", "했다");
        replacements.put("됩니다.", "된다.");
        replacements.put("됩니다", "된다");
        replacements.put("있습니다.", "있다.");
        replacements.put("있습니다", "있다");
        replacements.put("없습니다.", "없다.");
        replacements.put("없습니다", "없다");

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }

        return result;
    }
}
