package com.project.Transflow.publish.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.project.Transflow.publish.config.CreationKrProperties;
import com.project.Transflow.publish.dto.PublishResult;
import com.project.Transflow.publish.exception.CreationKrPublishException;
import com.project.Transflow.settings.dto.CreationKrCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreationKrBrowserClient {

    private static final int MIN_BODY_HTML_LENGTH = 50;

    private static final String FROALA_FILL_SCRIPT =
            "(el, html) => {"
                    + " el.focus();"
                    + " el.innerHTML = html;"
                    + " el.dispatchEvent(new InputEvent('input', { bubbles: true }));"
                    + " el.dispatchEvent(new Event('change', { bubbles: true }));"
                    + " el.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));"
                    + " const wrapper = document.querySelector('#post_body .fr-wrapper');"
                    + " if (wrapper) { wrapper.classList.remove('show-placeholder'); }"
                    + " const counter = document.querySelector('.fr-counter')?.textContent?.trim() || '';"
                    + " const len = el.innerHTML?.length || 0;"
                    + " const counterOk = counter.length > 0 && !(/:\\\\s*0\\\\s*$/.test(counter));"
                    + " return counterOk || len > 50;"
                    + " }";

    private static final String FROALA_EXEC_COMMAND_SCRIPT =
            "(el, html) => {"
                    + " el.focus();"
                    + " el.innerHTML = '';"
                    + " const ok = document.execCommand('insertHTML', false, html);"
                    + " el.dispatchEvent(new InputEvent('input', { bubbles: true }));"
                    + " el.dispatchEvent(new Event('change', { bubbles: true }));"
                    + " const wrapper = document.querySelector('#post_body .fr-wrapper');"
                    + " if (wrapper) { wrapper.classList.remove('show-placeholder'); }"
                    + " const counter = document.querySelector('.fr-counter')?.textContent?.trim() || '';"
                    + " const len = el.innerHTML?.length || 0;"
                    + " const counterOk = counter.length > 0 && !(/:\\\\s*0\\\\s*$/.test(counter));"
                    + " return ok && (counterOk || len > 50);"
                    + " }";

    private final CreationKrProperties properties;

    private Playwright playwright;

    private Playwright getPlaywright() {
        if (playwright == null) {
            playwright = Playwright.create();
        }
        return playwright;
    }

    @PreDestroy
    public void cleanup() {
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception e) {
                log.warn("Playwright 종료 중 오류: {}", e.getMessage());
            }
        }
    }

    /**
     * DB에 저장된 계정으로 creation.kr 로그인 테스트
     */
    public PublishResult testLogin(CreationKrCredentials credentials) {
        Browser browser = null;
        try {
            browser = launchBrowser();
            BrowserContext context = newBrowserContext(browser);
            Page page = context.newPage();
            page.setDefaultTimeout(properties.getTimeoutMs());

            page.navigate(properties.getBaseUrl());
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            boolean loggedIn = performLoginAndReachTarget(page, credentials, properties.getBaseUrl());
            if (!loggedIn) {
                return PublishResult.failure("creation.kr 로그인에 실패했습니다. 계정 정보 또는 사이트 UI를 확인해주세요.");
            }
            return PublishResult.success(page.url());
        } catch (Exception e) {
            log.error("creation.kr 로그인 테스트 실패", e);
            return PublishResult.failure("로그인 테스트 중 오류: " + e.getMessage());
        } finally {
            closeBrowser(browser);
        }
    }

    /**
     * creation.kr 게시판에 글 등록
     */
    public PublishResult publishPost(CreationKrCredentials credentials, String sitePath, String boardId,
                                   String title, String htmlContent) {
        if (!properties.isEnabled()) {
            return PublishResult.failure("creation.kr 자동 게시 기능이 비활성화되어 있습니다.");
        }
        if (boardId == null || boardId.isBlank()) {
            return PublishResult.failure("boardId가 설정되지 않았습니다. sitePath: " + sitePath);
        }

        Browser browser = null;
        try {
            browser = launchBrowser();
            BrowserContext context = newBrowserContext(browser);
            Page page = context.newPage();
            page.setDefaultTimeout(properties.getTimeoutMs());

            String writeUrl = properties.buildWriteUrl(sitePath, boardId);
            log.info("creation.kr 글쓰기 페이지 이동: {}", writeUrl);
            navigateToWritePage(page, writeUrl, credentials);

            fillTitle(page, title);
            fillContent(page, htmlContent);
            submitPost(page);
            waitForPostSubmitNavigation(page);

            String resultUrl = page.url();
            log.info("creation.kr 게시 완료 (추정 URL): {}", resultUrl);

            if (resultUrl.contains("bmode=write")) {
                String pageError = extractPageErrorMessage(page);
                String message = "게시 후에도 글쓰기 페이지에 머물러 있습니다.";
                if (pageError != null && !pageError.isBlank()) {
                    message += " 사이트 메시지: " + pageError;
                } else {
                    message += " 본문 동기화 또는 등록 확인이 필요합니다.";
                }
                return PublishResult.failure(message);
            }

            return PublishResult.success(resultUrl);
        } catch (CreationKrPublishException e) {
            log.error("creation.kr 게시 실패 - sitePath: {}, boardId: {}, message: {}",
                    sitePath, boardId, e.getMessage());
            return PublishResult.failure("게시 중 오류: " + e.getMessage());
        } catch (Exception e) {
            log.error("creation.kr 게시 실패 - sitePath: {}, boardId: {}", sitePath, boardId, e);
            return PublishResult.failure("게시 중 오류: " + e.getMessage());
        } finally {
            closeBrowser(browser);
        }
    }

    private void navigateToWritePage(Page page, String writeUrl, CreationKrCredentials credentials) {
        page.navigate(writeUrl);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);

        for (int attempt = 0; attempt < 2; attempt++) {
            if (hasWriteForm(page)) {
                waitForWriteForm(page);
                return;
            }

            if (needsAuthentication(page)) {
                log.info("로그인 필요 — 시도 {}/2, URL: {}", attempt + 1, page.url());
                if (!performLoginAndReachTarget(page, credentials, writeUrl)) {
                    throw new CreationKrPublishException("creation.kr 로그인에 실패했습니다.");
                }
            }

            if (!hasWriteForm(page)) {
                String targetUrl = resolvePostLoginTarget(page, writeUrl);
                log.info("글쓰기 페이지 재이동: {}", targetUrl);
                page.navigate(targetUrl);
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            }
        }

        if (needsAuthentication(page)) {
            throw new CreationKrPublishException(
                    "로그인 후에도 글쓰기 페이지에 접근할 수 없습니다. URL: " + page.url());
        }

        waitForWriteForm(page);
    }

    private void waitForWriteForm(Page page) {
        CreationKrProperties.Selectors selectors = properties.getSelectors();
        if (!waitForAnySelector(page, selectors.getWriteTitle())) {
            log.warn("글쓰기 폼(제목) 대기 실패. 현재 URL: {}", page.url());
            throw new CreationKrPublishException(
                    "글쓰기 폼을 찾을 수 없습니다. 제목 필드(#post_subject)가 표시되지 않습니다. URL: " + page.url());
        }
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE);
        } catch (Exception e) {
            log.debug("NETWORKIDLE 대기 스킵: {}", e.getMessage());
        }
        waitBriefly();
    }

    private boolean performLoginAndReachTarget(Page page, CreationKrCredentials credentials, String targetUrl) {
        if (isLoggedIn(page) && !needsAuthentication(page)) {
            return true;
        }

        ensureLoginFormVisible(page, targetUrl);

        CreationKrProperties.Selectors selectors = properties.getSelectors();
        Locator emailInput = waitForVisibleLocator(page, selectors.getEmailInput());
        Locator passwordInput = waitForVisibleLocator(page, selectors.getPasswordInput());

        if (emailInput == null || passwordInput == null) {
            log.warn("로그인 폼을 찾을 수 없습니다. URL: {}", page.url());
            return false;
        }

        emailInput.fill(credentials.getEmail());
        passwordInput.fill(credentials.getPassword());

        Locator submitButton = firstVisibleLocator(page, selectors.getLoginSubmit());
        if (submitButton != null) {
            submitButton.click();
        } else {
            passwordInput.press("Enter");
        }

        waitForPostLoginNavigation(page, resolvePostLoginTarget(page, targetUrl));
        waitMillis(1000);

        if (detectLoginFailure(page)) {
            String loginError = extractLoginErrorMessage(page);
            log.warn("creation.kr 로그인 실패. URL: {}, message: {}", page.url(), loginError);
            return false;
        }

        return verifyLoginSuccess(page);
    }

    private void ensureLoginFormVisible(Page page, String targetUrl) {
        if (waitForAnySelector(page, properties.getSelectors().getPasswordInput())) {
            return;
        }

        if (isLoginPage(page)) {
            waitBriefly();
            if (waitForAnySelector(page, properties.getSelectors().getPasswordInput())) {
                return;
            }
        }

        tryClickLoginLink(page, properties.getSelectors().getLoginLink());
        if (waitForAnySelector(page, properties.getSelectors().getPasswordInput())) {
            return;
        }

        String loginUrl = buildLoginUrlWithBackUrl(targetUrl);
        log.info("로그인 페이지로 이동: {}", loginUrl);
        page.navigate(loginUrl);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        waitForAnySelector(page, properties.getSelectors().getPasswordInput());
    }

    private void waitForPostLoginNavigation(Page page, String targetUrl) {
        long deadline = System.currentTimeMillis() + Math.min(properties.getTimeoutMs(), 30000L);
        while (System.currentTimeMillis() < deadline) {
            if (hasWriteForm(page)) {
                return;
            }
            if (verifyLoginSuccess(page) && !isLoginPage(page)) {
                return;
            }
            waitMillis(500);
        }

        if (needsAuthentication(page) || isLoginPage(page)) {
            log.info("로그인 후 대상 페이지로 이동: {}", targetUrl);
            page.navigate(targetUrl);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            waitBriefly();
        }
    }

    private String resolvePostLoginTarget(Page page, String fallbackWriteUrl) {
        String backUrlPath = extractBackUrlPath(page);
        if (backUrlPath != null && !backUrlPath.isBlank()) {
            return toAbsoluteUrl(backUrlPath);
        }
        return fallbackWriteUrl;
    }

    private String buildLoginUrlWithBackUrl(String targetUrl) {
        String path = targetUrl.replace(properties.getBaseUrl(), "");
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String encoded = Base64.getEncoder().encodeToString(path.getBytes(StandardCharsets.UTF_8));
        return properties.getBaseUrl() + "/?mode=login&back_url="
                + URLEncoder.encode(encoded, StandardCharsets.UTF_8);
    }

    private String extractBackUrlPath(Page page) {
        try {
            URI uri = URI.create(page.url());
            String query = uri.getRawQuery();
            if (query == null) {
                return null;
            }
            for (String param : query.split("&")) {
                if (param.startsWith("back_url=")) {
                    String value = param.substring("back_url=".length());
                    String urlDecoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
                    byte[] decoded = Base64.getDecoder().decode(urlDecoded);
                    return new String(decoded, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.debug("back_url 파싱 실패: {}", e.getMessage());
        }
        return null;
    }

    private String toAbsoluteUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return properties.getBaseUrl();
        }
        if (pathOrUrl.startsWith("http")) {
            return pathOrUrl;
        }
        return properties.getBaseUrl() + (pathOrUrl.startsWith("/") ? pathOrUrl : "/" + pathOrUrl);
    }

    private boolean hasWriteForm(Page page) {
        return firstVisibleLocator(page, properties.getSelectors().getWriteTitle()) != null;
    }

    private boolean isLoginPage(Page page) {
        return page.url().contains("mode=login");
    }

    private boolean needsAuthentication(Page page) {
        return isLoginPage(page) || isLoginRequired(page);
    }

    private boolean verifyLoginSuccess(Page page) {
        if (detectLoginFailure(page)) {
            return false;
        }
        if (isLoggedIn(page)) {
            return true;
        }
        return hasWriteForm(page);
    }

    private Browser launchBrowser() {
        BrowserType browserType = getPlaywright().chromium();
        return browserType.launch(new BrowserType.LaunchOptions()
                .setHeadless(properties.isHeadless())
                .setTimeout(properties.getTimeoutMs())
                .setArgs(Arrays.asList(
                        "--disable-blink-features=AutomationControlled",
                        "--disable-dev-shm-usage",
                        "--no-sandbox"
                )));
    }

    private BrowserContext newBrowserContext(Browser browser) {
        return browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080)
                .setLocale("ko-KR")
                .setTimezoneId("Asia/Seoul"));
    }

    private void closeBrowser(Browser browser) {
        if (browser != null) {
            try {
                browser.close();
            } catch (Exception e) {
                log.warn("브라우저 종료 실패: {}", e.getMessage());
            }
        }
    }

    private void tryClickLoginLink(Page page, String selector) {
        Locator loginLink = firstVisibleLocator(page, selector);
        if (loginLink != null) {
            try {
                loginLink.click();
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                waitBriefly();
            } catch (Exception e) {
                log.debug("로그인 링크 클릭 실패: {}", e.getMessage());
            }
        }
    }

    private void fillTitle(Page page, String title) {
        Locator titleInput = waitForVisibleLocator(page, properties.getSelectors().getWriteTitle());
        if (titleInput == null) {
            throw new CreationKrPublishException("제목 입력 필드를 찾을 수 없습니다.");
        }
        titleInput.fill(title);
    }

    private void fillContent(Page page, String htmlContent) {
        if (!fillFroalaContent(page, htmlContent)) {
            throw new CreationKrPublishException("본문 입력(Froala)에 실패했습니다.");
        }
        verifyBodyContent(page);
        log.info("Froala 본문 입력 검증 통과: {}", describeBodyContent(page));
    }

    private boolean fillFroalaContent(Page page, String htmlContent) {
        Locator editor = waitForVisibleLocator(page, "#post_body .fr-element.fr-view");
        if (editor == null) {
            editor = waitForVisibleLocator(page, properties.getSelectors().getWriteBody());
        }
        if (editor == null) {
            log.warn("Froala editor element를 찾을 수 없습니다.");
            return false;
        }

        try {
            Object result = editor.evaluate(FROALA_FILL_SCRIPT, htmlContent);
            if (Boolean.TRUE.equals(result)) {
                log.info("Froala 본문 입력 — innerHTML + InputEvent");
                return true;
            }
            log.warn("Froala innerHTML 방식 동기화 실패, execCommand fallback 시도");
        } catch (Exception e) {
            log.warn("Froala innerHTML 입력 실패: {}", e.getMessage());
        }

        try {
            Object result = editor.evaluate(FROALA_EXEC_COMMAND_SCRIPT, htmlContent);
            if (Boolean.TRUE.equals(result)) {
                log.info("Froala 본문 입력 — execCommand insertHTML");
                return true;
            }
        } catch (Exception e) {
            log.warn("Froala execCommand 입력 실패: {}", e.getMessage());
        }

        return false;
    }

    private void verifyBodyContent(Page page) {
        BodyContentStats stats = readBodyContentStats(page);
        if (stats.htmlLength() >= MIN_BODY_HTML_LENGTH && stats.counterSynced()) {
            return;
        }
        throw new CreationKrPublishException(
                "본문이 에디터에 반영되지 않았습니다. (htmlLength="
                        + stats.htmlLength() + ", counter=" + stats.counterText() + ")"
        );
    }

    private String describeBodyContent(Page page) {
        BodyContentStats stats = readBodyContentStats(page);
        return "htmlLength=" + stats.htmlLength() + ", counter=" + stats.counterText();
    }

    private BodyContentStats readBodyContentStats(Page page) {
        Object raw = page.evaluate(
                "() => {"
                        + " const el = document.querySelector('#post_body .fr-element.fr-view')"
                        + "   || document.querySelector('#post_body .fr-element');"
                        + " const counter = document.querySelector('.fr-counter')?.textContent?.trim() || '';"
                        + " const htmlLength = el?.innerHTML?.length || 0;"
                        + " const counterSynced = counter.length > 0 && !(/:\\\\s*0\\\\s*$/.test(counter));"
                        + " return { htmlLength, counter, counterSynced };"
                        + " }"
        );
        if (raw instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) raw;
            int htmlLength = 0;
            Object htmlLengthValue = map.get("htmlLength");
            if (htmlLengthValue instanceof Number) {
                htmlLength = ((Number) htmlLengthValue).intValue();
            }
            String counter = map.get("counter") != null ? map.get("counter").toString() : "";
            boolean counterSynced = Boolean.TRUE.equals(map.get("counterSynced"));
            return new BodyContentStats(htmlLength, counter, counterSynced);
        }
        return new BodyContentStats(0, "", false);
    }

    private static final class BodyContentStats {
        private final int htmlLength;
        private final String counterText;
        private final boolean counterSynced;

        private BodyContentStats(int htmlLength, String counterText, boolean counterSynced) {
            this.htmlLength = htmlLength;
            this.counterText = counterText;
            this.counterSynced = counterSynced;
        }

        private int htmlLength() {
            return htmlLength;
        }

        private String counterText() {
            return counterText;
        }

        private boolean counterSynced() {
            return counterSynced;
        }
    }

    private void submitPost(Page page) {
        Locator submitButton = waitForVisibleLocator(page, properties.getSelectors().getWriteSubmit());
        if (submitButton == null) {
            throw new CreationKrPublishException("등록 버튼을 찾을 수 없습니다.");
        }
        submitButton.click();
        waitMillis(800);
        tryClickConfirmDialog(page);
    }

    private void tryClickConfirmDialog(Page page) {
        for (String selector : List.of("text=확인", "button:has-text('확인')", "text=등록")) {
            Locator confirmButton = firstVisibleLocator(page, selector);
            if (confirmButton != null) {
                try {
                    confirmButton.click();
                    log.info("게시 확인 dialog 클릭: {}", selector);
                    waitMillis(800);
                    return;
                } catch (Exception e) {
                    log.debug("확인 dialog 클릭 실패 ({}): {}", selector, e.getMessage());
                }
            }
        }
    }

    private void waitForPostSubmitNavigation(Page page) {
        long deadline = System.currentTimeMillis() + Math.min(properties.getTimeoutMs(), 45000L);
        while (System.currentTimeMillis() < deadline) {
            try {
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            } catch (Exception e) {
                log.debug("DOMCONTENTLOADED 대기 스킵: {}", e.getMessage());
            }
            if (!page.url().contains("bmode=write")) {
                return;
            }
            waitMillis(500);
        }
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE);
        } catch (Exception e) {
            log.debug("NETWORKIDLE 대기 스킵: {}", e.getMessage());
        }
    }

    private String extractPageErrorMessage(Page page) {
        try {
            Object message = page.evaluate(
                    "() => {"
                            + " const selectors = ["
                            + "   '.alert-danger', '.alert-warning', '.error-msg', '.validation-error',"
                            + "   '[class*=\"error\"]', '[class*=\"alert\"]'"
                            + " ];"
                            + " for (const sel of selectors) {"
                            + "   const el = document.querySelector(sel);"
                            + "   const text = el?.textContent?.trim();"
                            + "   if (text && text.length > 0 && text.length < 500) { return text; }"
                            + " }"
                            + " return null;"
                            + " }"
            );
            return message != null ? message.toString() : null;
        } catch (Exception e) {
            log.debug("페이지 에러 메시지 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    private boolean isLoginRequired(Page page) {
        return firstVisibleLocator(page, properties.getSelectors().getPasswordInput()) != null;
    }

    private boolean isLoggedIn(Page page) {
        if (needsAuthentication(page)) {
            return false;
        }
        Locator logoutLink = firstVisibleLocator(page, "a[href*='logout'], a[href*='Logout']");
        if (logoutLink != null) {
            return true;
        }
        Locator indicator = firstVisibleLocator(page, properties.getSelectors().getLoggedInIndicator());
        return indicator != null;
    }

    private boolean detectLoginFailure(Page page) {
        if (!needsAuthentication(page) && !isLoginPage(page)) {
            return false;
        }
        String loginError = extractLoginErrorMessage(page);
        if (loginError != null && !loginError.isBlank()) {
            return true;
        }
        return isLoginRequired(page);
    }

    private String extractLoginErrorMessage(Page page) {
        try {
            Object message = page.evaluate(
                    "() => {"
                            + " const selectors = ["
                            + "   '.alert-danger', '.alert-warning', '.error-msg', '.validation-error',"
                            + "   '.login-error', '.modal-body .text-danger', '[class*=\"error\"]'"
                            + " ];"
                            + " for (const sel of selectors) {"
                            + "   const nodes = document.querySelectorAll(sel);"
                            + "   for (const el of nodes) {"
                            + "     const style = window.getComputedStyle(el);"
                            + "     if (style.display === 'none' || style.visibility === 'hidden') { continue; }"
                            + "     const text = el.textContent?.trim();"
                            + "     if (text && text.length > 0 && text.length < 500) { return text; }"
                            + "   }"
                            + " }"
                            + " const bodyText = document.body?.innerText || '';"
                            + " const patterns = ['비밀번호', '일치하지', '로그인에 실패', '아이디', '확인해'];"
                            + " for (const p of patterns) {"
                            + "   if (bodyText.includes(p) && (bodyText.includes('틀') || bodyText.includes('실패') || bodyText.includes('일치'))) {"
                            + "     return bodyText.split('\\n').find(line => line.includes(p)) || p;"
                            + "   }"
                            + " }"
                            + " return null;"
                            + " }"
            );
            return message != null ? message.toString() : null;
        } catch (Exception e) {
            log.debug("로그인 에러 메시지 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    private Locator waitForVisibleLocator(Page page, String combinedSelectors) {
        if (waitForAnySelector(page, combinedSelectors)) {
            return firstVisibleLocator(page, combinedSelectors);
        }
        return null;
    }

    private boolean waitForAnySelector(Page page, String combinedSelectors) {
        List<String> selectors = Arrays.asList(combinedSelectors.split(","));
        long perSelectorTimeout = Math.max(5000L, properties.getTimeoutMs() / selectors.size());
        for (String raw : selectors) {
            String selector = raw.trim();
            if (selector.isEmpty()) {
                continue;
            }
            try {
                if (selector.startsWith("text=")) {
                    page.getByText(Pattern.compile(selector.substring(5).trim()))
                            .first()
                            .waitFor(new Locator.WaitForOptions()
                                    .setState(WaitForSelectorState.VISIBLE)
                                    .setTimeout(perSelectorTimeout));
                    return true;
                }
                page.locator(selector)
                        .first()
                        .waitFor(new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.VISIBLE)
                                .setTimeout(perSelectorTimeout));
                return true;
            } catch (Exception e) {
                log.debug("selector 대기 실패: {} - {}", selector, e.getMessage());
            }
        }
        return false;
    }

    private Locator firstVisibleLocator(Page page, String combinedSelectors) {
        List<String> selectors = Arrays.asList(combinedSelectors.split(","));
        for (String raw : selectors) {
            String selector = raw.trim();
            if (selector.isEmpty()) {
                continue;
            }
            try {
                Locator locator;
                if (selector.startsWith("text=")) {
                    locator = page.getByText(Pattern.compile(selector.substring(5).trim()));
                } else {
                    locator = page.locator(selector);
                }
                if (locator.count() > 0 && locator.first().isVisible()) {
                    return locator.first();
                }
            } catch (Exception e) {
                log.trace("selector 실패: {} - {}", selector, e.getMessage());
            }
        }
        return null;
    }

    private void waitBriefly() {
        waitMillis(1500);
    }

    private void waitMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
