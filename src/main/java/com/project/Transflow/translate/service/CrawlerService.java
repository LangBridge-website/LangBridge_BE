package com.project.Transflow.translate.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class CrawlerService {

    private static final int TIMEOUT = 30000; // 30 seconds (Playwright는 더 오래 걸릴 수 있음)
    
    // Playwright 인스턴스를 재사용하기 위한 필드 (스레드 안전하게 관리 필요)
    private Playwright playwright;

    public CrawlerService() {
        try {
            // Playwright 브라우저 자동 설치 (첫 실행 시)
            installPlaywrightBrowsersIfNeeded();
            
            // Playwright 인스턴스 생성 및 브라우저 설치 확인
            this.playwright = Playwright.create();
            log.info("Playwright 초기화 완료");
        } catch (Exception e) {
            log.warn("Playwright 초기화 실패. Jsoup으로 대체됩니다: {}", e.getMessage());
            this.playwright = null;
        }
    }
    
    /**
     * Playwright 브라우저 자동 설치
     */
    private void installPlaywrightBrowsersIfNeeded() {
        try {
            // 브라우저가 설치되어 있는지 확인하고, 없으면 설치
            log.info("Playwright 브라우저 설치 확인 중...");
            
            // CLI를 통한 브라우저 설치
            // 이미 설치되어 있으면 스킵됨
            ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-cp",
                System.getProperty("java.class.path"),
                "com.microsoft.playwright.CLI",
                "install",
                "chromium"
            );
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.info("Playwright 브라우저 설치 완료 또는 이미 설치됨");
            } else {
                log.warn("Playwright 브라우저 설치 실패. 수동 설치가 필요할 수 있습니다.");
            }
        } catch (Exception e) {
            log.warn("Playwright 브라우저 자동 설치 실패: {}. 수동 설치를 시도하세요.", e.getMessage());
        }
    }

    /**
     * 웹페이지의 HTML과 CSS를 함께 가져오는 메서드 (Playwright 사용)
     * @param url 크롤링할 URL
     * @return Map containing "html" and "css" keys
     */
    public Map<String, String> crawlWebPageWithStyles(String url) {
        if (playwright == null) {
            log.error("Playwright가 초기화되지 않았습니다. Playwright 설치가 필요합니다.");
            throw new RuntimeException("Playwright가 설치되지 않았습니다. 백엔드 설정을 확인해주세요.");
        }

        Browser browser = null;
        Page page = null;
        try {
            log.info("Playwright로 크롤링 시작: {}", url);

            // 브라우저 실행 (headless 모드, 실제 브라우저처럼 보이게 설정)
            BrowserType browserType = playwright.chromium();
            browser = browserType.launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setTimeout(30000) // 브라우저 시작 타임아웃 30초
                    .setArgs(java.util.Arrays.asList(
                            "--disable-blink-features=AutomationControlled", // 자동화 감지 방지
                            "--disable-dev-shm-usage",
                            "--no-sandbox",
                            "--disable-setuid-sandbox"
                    )));

            // 브라우저 컨텍스트 생성 (쿠키, 세션 관리)
            com.microsoft.playwright.BrowserContext context = browser.newContext(
                    new com.microsoft.playwright.Browser.NewContextOptions()
                            .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .setViewportSize(1920, 1080)
                            .setLocale("en-US")
                            .setTimezoneId("America/New_York")
                            .setExtraHTTPHeaders(java.util.Map.of(
                                    "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                                    "Accept-Language", "en-US,en;q=0.9",
                                    "Accept-Encoding", "gzip, deflate, br",
                                    "Connection", "keep-alive",
                                    "Upgrade-Insecure-Requests", "1",
                                    "Sec-Fetch-Dest", "document",
                                    "Sec-Fetch-Mode", "navigate",
                                    "Sec-Fetch-Site", "none",
                                    "Cache-Control", "max-age=0"
                            ))
            );

            // 새 페이지 생성
            page = context.newPage();
            
            // 페이지 타임아웃 설정 (5분)
            page.setDefaultTimeout(300000);

            // 자동화 감지 방지를 위한 JavaScript 실행
            page.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined});");
            page.addInitScript("window.chrome = {runtime: {}};");
            page.addInitScript("Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});");

            // 페이지 로드
            log.info("페이지 로드 중...");
            try {
                page.navigate(url);
            } catch (Exception e) {
                log.warn("페이지 로드 중 오류 발생: {}. 현재 페이지 내용을 가져옵니다.", e.getMessage());
                // 타임아웃이 발생해도 현재 페이지 내용은 가져올 수 있음
            }

            // Cloudflare 검증 대기 (최대 30초 추가 대기)
            log.info("Cloudflare 검증 대기 중...");
            String html = "";
            boolean isCloudflare = false;
            
            for (int attempt = 0; attempt < 6; attempt++) { // 최대 6번 시도 (총 30초)
                try {
                    Thread.sleep(5000); // 5초 대기
                    html = page.content();
                    
                    // Cloudflare 검증 페이지인지 확인
                    String htmlLower = html.toLowerCase();
                    isCloudflare = htmlLower.contains("verify you are human") || 
                                  htmlLower.contains("enable javascript and cookies") ||
                                  htmlLower.contains("just a moment") ||
                                  htmlLower.contains("checking your browser") ||
                                  htmlLower.contains("ray id:");
                    
                    if (!isCloudflare) {
                        // Cloudflare가 아니면 추가 대기 후 HTML 가져오기
                        try {
                            // 추가 대기 시간 (네트워크 요청 완료 대기)
                            Thread.sleep(2000);
                            html = page.content(); // 최신 HTML 가져오기
                            log.info("Cloudflare 검증 완료 또는 검증 불필요");
                        } catch (Exception e) {
                            log.debug("HTML 가져오기 실패, 현재 HTML 사용: {}", e.getMessage());
                            html = page.content(); // 최소한 현재 내용이라도 가져오기
                        }
                        break;
                    } else {
                        log.info("Cloudflare 검증 페이지 감지됨. 대기 중... (시도 {}/6)", attempt + 1);
                    }
                } catch (Exception e) {
                    log.warn("HTML 가져오기 실패: {}", e.getMessage());
                    if (html.isEmpty()) {
                        html = page.content(); // 최소한 현재 내용이라도 가져오기
                    }
                }
            }
            
            // 최종 HTML 가져오기
            if (html.isEmpty()) {
                html = page.content();
            }
            
            if (isCloudflare) {
                log.warn("Cloudflare 검증 페이지가 반환됩니다. 사용자에게 표시됩니다.");
            }
            
            // CSS 추출 (스타일 태그와 외부 스타일시트)
            String css = "";
            try {
                css = extractAllCSS(page, html);
            } catch (Exception e) {
                log.warn("CSS 추출 실패: {}", e.getMessage());
                css = "";
            }

            log.info("크롤링 완료. HTML 길이: {}, CSS 길이: {}, Cloudflare: {}", 
                    html.length(), css.length(), isCloudflare);

            Map<String, String> result = new HashMap<>();
            result.put("html", html);
            result.put("css", css);
            
            return result;

        } catch (Exception e) {
            // 타임아웃이 발생해도 현재 페이지 내용은 반환
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                log.warn("Playwright 타임아웃 발생: {}. 현재 페이지 내용을 반환합니다.", url);
                if (page != null) {
                    try {
                        String html = page.content();
                        String css = "";
                        try {
                            css = extractAllCSS(page, html);
                        } catch (Exception ex) {
                            log.warn("CSS 추출 실패: {}", ex.getMessage());
                        }
                        
                        Map<String, String> result = new HashMap<>();
                        result.put("html", html);
                        result.put("css", css);
                        log.warn("타임아웃 발생했지만 현재 페이지 내용을 반환합니다.");
                        return result;
                    } catch (Exception ex) {
                        log.error("페이지 내용 가져오기 실패: {}", ex.getMessage());
                    }
                }
            }
            log.error("Playwright 크롤링 실패: {}", url, e);
            throw new RuntimeException("크롤링 실패: " + e.getMessage(), e);
        } finally {
            if (browser != null) {
                try {
                    browser.close();
                } catch (Exception e) {
                    log.warn("브라우저 종료 실패: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * CSS를 추출하는 헬퍼 메서드
     */
    private String extractAllCSS(Page page, String html) {
        StringBuilder cssBuilder = new StringBuilder();

        try {
            // 1. <style> 태그 내의 CSS 추출
            Document doc = Jsoup.parse(html);
            Elements styleTags = doc.select("style");
            for (Element styleTag : styleTags) {
                cssBuilder.append(styleTag.html()).append("\n");
            }

            // 2. 외부 CSS 링크 추출 및 다운로드
            Elements linkTags = doc.select("link[rel=stylesheet]");
            for (Element linkTag : linkTags) {
                String href = linkTag.attr("href");
                if (href != null && !href.isEmpty()) {
                    try {
                        // 상대 URL을 절대 URL로 변환
                        String absoluteUrl = resolveUrl(page.url(), href);
                        String cssContent = fetchCSS(absoluteUrl);
                        if (cssContent != null && !cssContent.isEmpty()) {
                            cssBuilder.append("\n/* External CSS from: ").append(absoluteUrl).append(" */\n");
                            cssBuilder.append(cssContent).append("\n");
                        }
                    } catch (Exception e) {
                        log.warn("CSS 다운로드 실패: {}", href, e);
                    }
                }
            }

            // 3. 인라인 스타일 속성도 유지 (HTML에 포함되어 있음)

        } catch (Exception e) {
            log.warn("CSS 추출 중 오류 발생: {}", e.getMessage());
        }

        return cssBuilder.toString();
    }

    /**
     * 상대 URL을 절대 URL로 변환
     */
    private String resolveUrl(String baseUrl, String relativeUrl) {
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }
        if (relativeUrl.startsWith("//")) {
            return "https:" + relativeUrl;
        }
        
        try {
            java.net.URL base = new java.net.URL(baseUrl);
            return new java.net.URL(base, relativeUrl).toString();
        } catch (Exception e) {
            log.warn("URL 변환 실패: base={}, relative={}", baseUrl, relativeUrl);
            return relativeUrl;
        }
    }

    /**
     * 외부 CSS 파일 다운로드
     */
    private String fetchCSS(String cssUrl) {
        try {
            Document doc = Jsoup.connect(cssUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .ignoreContentType(true)
                    .get();
            return doc.body().text();
        } catch (Exception e) {
            log.warn("CSS 다운로드 실패: {}", cssUrl);
            return null;
        }
    }

    /**
     * Fallback: Jsoup을 사용한 크롤링 (비활성화됨 - Playwright만 사용)
     * @deprecated Playwright만 사용하도록 변경됨
     */
    @Deprecated
    private Map<String, String> crawlWebPageWithStylesFallback(String url) {
        try {
            log.info("Jsoup으로 크롤링 시작: {}", url);

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(TIMEOUT)
                    .get();

            // HTML 가져오기 (스크립트는 제거하되 스타일은 유지)
            doc.select("script").remove();
            String html = doc.html();

            // CSS 추출
            StringBuilder cssBuilder = new StringBuilder();
            Elements styleTags = doc.select("style");
            for (Element styleTag : styleTags) {
                cssBuilder.append(styleTag.html()).append("\n");
            }

            // 외부 CSS 링크 추출
            Elements linkTags = doc.select("link[rel=stylesheet]");
            for (Element linkTag : linkTags) {
                String href = linkTag.attr("href");
                if (href != null && !href.isEmpty()) {
                    try {
                        String absoluteUrl = resolveUrl(url, href);
                        String cssContent = fetchCSS(absoluteUrl);
                        if (cssContent != null && !cssContent.isEmpty()) {
                            cssBuilder.append("\n/* External CSS from: ").append(absoluteUrl).append(" */\n");
                            cssBuilder.append(cssContent).append("\n");
                        }
                    } catch (Exception e) {
                        log.warn("CSS 다운로드 실패: {}", href);
                    }
                }
            }

            Map<String, String> result = new HashMap<>();
            result.put("html", html);
            result.put("css", cssBuilder.toString());

            log.info("Jsoup 크롤링 완료. HTML 길이: {}, CSS 길이: {}", html.length(), cssBuilder.length());
            return result;

        } catch (Exception e) {
            log.error("Jsoup 크롤링 실패: {}", url, e);
            throw new RuntimeException("웹페이지 크롤링 중 오류 발생: " + e.getMessage());
        }
    }

    /**
     * 기존 메서드 (하위 호환성 유지)
     * 텍스트만 추출하는 경우 사용
     * Playwright만 사용 (Jsoup fallback 제거)
     */
    public String crawlWebPage(String url) {
        Map<String, String> result = crawlWebPageWithStyles(url);
        Document doc = Jsoup.parse(result.get("html"));
        doc.select("script, style").remove();
        return doc.body().text();
    }

    /**
     * 리소스 정리
     */
    public void cleanup() {
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception e) {
                log.warn("Playwright 종료 중 오류: {}", e.getMessage());
            }
        }
    }
}