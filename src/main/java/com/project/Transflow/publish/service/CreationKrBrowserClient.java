package com.project.Transflow.publish.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.project.Transflow.publish.config.CreationKrProperties;
import com.project.Transflow.publish.dto.PublishResult;
import com.project.Transflow.publish.exception.CreationKrPublishException;
import com.project.Transflow.settings.dto.CreationKrCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Arrays;
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

            boolean loggedIn = performLogin(page, credentials);
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
            page.navigate(writeUrl);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            if (isLoginRequired(page)) {
                log.info("로그인 필요 — 로그인 시도");
                if (!performLogin(page, credentials)) {
                    return PublishResult.failure("creation.kr 로그인에 실패했습니다.");
                }
                page.navigate(writeUrl);
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            }

            if (isLoginRequired(page)) {
                return PublishResult.failure("로그인 후에도 글쓰기 페이지에 접근할 수 없습니다.");
            }

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
        } catch (Exception e) {
            log.error("creation.kr 게시 실패 - sitePath: {}, boardId: {}", sitePath, boardId, e);
            return PublishResult.failure("게시 중 오류: " + e.getMessage());
        } finally {
            closeBrowser(browser);
        }
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

    private boolean performLogin(Page page, CreationKrCredentials credentials) {
        CreationKrProperties.Selectors selectors = properties.getSelectors();

        if (!isLoginRequired(page) && isLoggedIn(page)) {
            return true;
        }

        tryClickLoginLink(page, selectors.getLoginLink());

        Locator emailInput = firstVisibleLocator(page, selectors.getEmailInput());
        Locator passwordInput = firstVisibleLocator(page, selectors.getPasswordInput());

        if (emailInput == null || passwordInput == null) {
            page.navigate(properties.getBaseUrl());
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            tryClickLoginLink(page, selectors.getLoginLink());
            emailInput = firstVisibleLocator(page, selectors.getEmailInput());
            passwordInput = firstVisibleLocator(page, selectors.getPasswordInput());
        }

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

        page.waitForLoadState(LoadState.NETWORKIDLE);
        waitBriefly();

        return isLoggedIn(page) || !isLoginRequired(page);
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
        Locator titleInput = firstVisibleLocator(page, properties.getSelectors().getWriteTitle());
        if (titleInput == null) {
            throw new CreationKrPublishException("제목 입력 필드를 찾을 수 없습니다.");
        }
        titleInput.fill(title);
    }

    private void fillContent(Page page, String htmlContent) {
        CreationKrProperties.Selectors selectors = properties.getSelectors();

        if (fillContentInIframe(page, htmlContent)) {
            return;
        }

        Locator bodyInput = firstVisibleLocator(page, selectors.getWriteBody());
        if (bodyInput != null) {
            try {
                bodyInput.fill(htmlContent);
                return;
            } catch (Exception e) {
                log.debug("textarea fill 실패, innerHTML 시도: {}", e.getMessage());
            }
        }

        if (fillContentEditable(page, htmlContent)) {
            return;
        }

        throw new CreationKrPublishException("본문 입력 필드를 찾을 수 없습니다.");
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

    private boolean fillContentEditable(Page page, String htmlContent) {
        try {
            Object filled = page.evaluate(
                    "(html) => {" +
                            "  const el = document.querySelector('[contenteditable=\"true\"], .note-editable');" +
                            "  if (!el) return false;" +
                            "  el.innerHTML = html;" +
                            "  return true;" +
                            "}",
                    htmlContent
            );
            return Boolean.TRUE.equals(filled);
        } catch (Exception e) {
            log.debug("contenteditable 입력 실패: {}", e.getMessage());
            return false;
        }
    }

    private void submitPost(Page page) {
        Locator submitButton = firstVisibleLocator(page, properties.getSelectors().getWriteSubmit());
        if (submitButton == null) {
            throw new CreationKrPublishException("등록 버튼을 찾을 수 없습니다.");
        }
        submitButton.click();
    }

    private boolean isLoginRequired(Page page) {
        String html = page.content().toLowerCase();
        boolean hasPasswordField = firstVisibleLocator(page, properties.getSelectors().getPasswordInput()) != null;
        boolean loginKeywords = html.contains("로그인") && (html.contains("password") || html.contains("비밀번호"));
        return hasPasswordField && loginKeywords;
    }

    private boolean isLoggedIn(Page page) {
        Locator indicator = firstVisibleLocator(page, properties.getSelectors().getLoggedInIndicator());
        if (indicator != null) {
            return true;
        }
        String html = page.content();
        return html.contains("로그아웃") || html.contains("logout");
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
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
