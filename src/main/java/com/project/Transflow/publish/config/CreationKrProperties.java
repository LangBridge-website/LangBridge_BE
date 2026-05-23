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

    /** sitePath → boardId (예: EvidenceofFlood → b201810315bd97ecb8e054) */
    private Map<String, String> boardMappings = new HashMap<>();

    private Selectors selectors = new Selectors();

    @Getter
    @Setter
    public static class Selectors {
        private String loginLink = "a[href*='login'], a[href*='member'], .login, text=로그인";
        private String emailInput = "input[type='email'], input[name='uid'], input[name='email'], input[name='id'], #member_id, #login_id";
        private String passwordInput = "input[type='password'], input[name='passwd'], input[name='password'], #member_pw, #login_pw";
        private String loginSubmit = "button[type='submit'], input[type='submit'], .btn_login, text=로그인";
        private String writeTitle = "input[name='subject'], input[name='title'], #subject, #title";
        private String writeBody = "textarea[name='body'], textarea[name='content'], #body, #content, .note-editable, [contenteditable='true']";
        private String writeSubmit = "button[type='submit'], input[type='submit'], .btn_submit, text=등록, text=저장, text=확인";
        private String loggedInIndicator = "a[href*='logout'], text=로그아웃, .member_info";
    }

    public String resolveBoardId(String sitePath) {
        if (sitePath == null || sitePath.isBlank()) {
            return null;
        }
        return boardMappings.get(sitePath.trim());
    }

    public String buildWriteUrl(String sitePath, String boardId) {
        String path = sitePath.startsWith("/") ? sitePath : "/" + sitePath;
        return baseUrl + path + "/?board=" + boardId + "&bmode=write";
    }
}
