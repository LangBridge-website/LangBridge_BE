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

            page.waitForLoadState(LoadState.NETWORKIDLE);
            String resultUrl = page.url();
            log.info("creation.kr 게시 완료 (추정 URL): {}", resultUrl);

            if (resultUrl.contains("bmode=write")) {
                return PublishResult.failure("게시 후에도 글쓰기 페이지에 머물러 있습니다. 폼 selector 확인이 필요합니다.");
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
        CreationKrProperties.Selectors selectors = properties.getSelectors();

        if (fillContentInEditableLocator(page, selectors.getWriteBody(), htmlContent)) {
            return;
        }

        if (fillContentInIframe(page, htmlContent)) {
            return;
        }

        Locator bodyInput = firstVisibleLocator(page, selectors.getWriteBody());
        if (bodyInput != null) {
            try {
                bodyInput.fill(htmlContent);
                return;
            } catch (Exception e) {
                log.debug("textarea fill 실패: {}", e.getMessage());
            }
        }

        throw new CreationKrPublishException("본문 입력 필드를 찾을 수 없습니다.");
    }

    private boolean fillContentInEditableLocator(Page page, String combinedSelectors, String htmlContent) {
        List<String> selectors = Arrays.asList(combinedSelectors.split(","));
        for (String raw : selectors) {
            String selector = raw.trim();
            if (selector.isEmpty() || selector.startsWith("text=")) {
                continue;
            }
            try {
                Locator locator = page.locator(selector);
                if (locator.count() == 0) {
                    continue;
                }
                Locator target = locator.first();
                target.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(properties.getTimeoutMs()));
                target.evaluate(
                        "(el, html) => { el.innerHTML = html; el.dispatchEvent(new Event('input', { bubbles: true })); }",
                        htmlContent
                );
                return true;
            } catch (Exception e) {
                log.debug("contenteditable locator 입력 실패 ({}): {}", selector, e.getMessage());
            }
        }
        return false;
    }

    private boolean fillContentInIframe(Page page, String htmlContent) {
        try {
            for (com.microsoft.playwright.Frame frame : page.frames()) {
                if (frame == page.mainFrame()) {
                    continue;
                }
                Locator body = frame.locator("body");
                if (body.count() > 0 && body.first().isVisible()) {
                    body.first().evaluate(
                            "(el, html) => { el.innerHTML = html; }",
                            htmlContent
                    );
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("iframe 본문 입력 실패: {}", e.getMessage());
        }
        return false;
    }

    private void submitPost(Page page) {
        Locator submitButton = waitForVisibleLocator(page, properties.getSelectors().getWriteSubmit());
        if (submitButton == null) {
            throw new CreationKrPublishException("등록 버튼을 찾을 수 없습니다.");
        }
        submitButton.click();
    }

    private boolean isLoginRequired(Page page) {
        return firstVisibleLocator(page, properties.getSelectors().getPasswordInput()) != null;
    }

    private boolean isLoggedIn(Page page) {
        Locator indicator = firstVisibleLocator(page, properties.getSelectors().getLoggedInIndicator());
        if (indicator != null) {
            return true;
        }
        String html = page.content();
        return html.contains("로그아웃") || html.contains("logout");
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
