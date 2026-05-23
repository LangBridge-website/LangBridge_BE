package com.project.Transflow.publish.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "creation-kr")
public class CreationKrProperties {

    private boolean enabled = true;
    private String baseUrl = "https://creation.kr";
    private int timeoutMs = 60000;
    private boolean headless = true;

    /** 시작 시 board-mappings 기준 Category 시드 (creationKr 필드 동기화) */
    private boolean seedCategories = true;

    /** sitePath → boardId (예: EvidenceofFlood → b201810315bd97ecb8e054) */
    private Map<String, String> boardMappings = new HashMap<>();

    /** sitePath → UI 표시명 (예: EvidenceofFlood → 대홍수) */
    private Map<String, String> boardLabels = new HashMap<>();

    private Selectors selectors = new Selectors();

    @Getter
    @Setter
    public static class Selectors {
        private String loginLink = "a[href*='login'], a[href*='member'], .login, text=로그인";
        private String emailInput = "input[name='uid'], input[title='이메일'], input[type='email'], input[name='email']";
        private String passwordInput = "input[name='passwd'], input[title='비밀번호'], input[type='password'], input[name='password']";
        private String loginSubmit = "button.btn-primary.btn-block, button.btn.btn-primary:has-text('로그인'), text=로그인";
        private String writeTitle = "#post_subject, input[name='subject'], input[name='title'], #subject, #title";
        private String writeBody = "#post_body .fr-element, #post_body [contenteditable='true'], textarea[name='body'], textarea[name='content'], .fr-element, [contenteditable='true']";
        private String writeSubmit = "button._save_post, button.save_post, text=작성, button[type='submit'], input[type='submit'], .btn_submit, text=등록, text=저장";
        private String loggedInIndicator = "a[href*='logout'], text=로그아웃, .member_info";
    }

    public String resolveBoardId(String sitePath) {
        if (sitePath == null || sitePath.isBlank()) {
            return null;
        }
        return boardMappings.get(sitePath.trim());
    }

    public String resolveBoardLabel(String sitePath) {
        if (sitePath == null || sitePath.isBlank()) {
            return sitePath;
        }
        String trimmed = sitePath.trim();
        String label = boardLabels != null ? boardLabels.get(trimmed) : null;
        return label != null && !label.isBlank() ? label : trimmed;
    }

    public String buildWriteUrl(String sitePath, String boardId) {
        String path = sitePath.startsWith("/") ? sitePath : "/" + sitePath;
        return baseUrl + path + "/?board=" + boardId + "&bmode=write";
    }
}
