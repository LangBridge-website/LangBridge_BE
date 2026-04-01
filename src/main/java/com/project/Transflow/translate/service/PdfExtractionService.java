package com.project.Transflow.translate.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * PDF URL에서 텍스트를 추출하여 HTML로 변환하는 서비스
 * Apache PDFBox를 사용하여 PDF 파일의 텍스트를 페이지별로 추출합니다.
 */
@Slf4j
@Service
public class PdfExtractionService {

    private static final int CONNECTION_TIMEOUT = 30_000;
    private static final int READ_TIMEOUT = 60_000;
    private static final int MAX_PDF_SIZE_BYTES = 50 * 1024 * 1024; // 50MB

    /**
     * PDF URL에서 텍스트를 추출하여 HTML 형식으로 반환합니다.
     * CrawlerService의 crawlWebPageWithStyles와 동일한 Map 구조를 반환합니다.
     *
     * @param pdfUrl PDF 파일 URL
     * @return Map with "html" and "css" keys (css는 항상 빈 문자열)
     */
    public Map<String, String> extractToHtml(String pdfUrl) {
        log.info("PDF 텍스트 추출 시작: {}", pdfUrl);

        try {
            byte[] pdfBytes = downloadPdf(pdfUrl);
            String html = convertPdfToHtml(pdfBytes, pdfUrl);

            Map<String, String> result = new HashMap<>();
            result.put("html", html);
            result.put("css", "");
            result.put("httpStatus", "200");

            log.info("PDF 텍스트 추출 완료: {} bytes → HTML {} chars", pdfBytes.length, html.length());
            return result;

        } catch (Exception e) {
            log.error("PDF 추출 실패: {}", pdfUrl, e);
            throw new RuntimeException("PDF 텍스트 추출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * URL에서 PDF 파일을 다운로드합니다.
     */
    private byte[] downloadPdf(String pdfUrl) throws Exception {
        URL url = new URL(pdfUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECTION_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Accept", "application/pdf,*/*");

        int status = conn.getResponseCode();
        if (status >= 400) {
            throw new RuntimeException("PDF 다운로드 실패: HTTP " + status);
        }

        try (InputStream in = conn.getInputStream()) {
            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int bytesRead;
            int totalBytes = 0;
            while ((bytesRead = in.read(buffer)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > MAX_PDF_SIZE_BYTES) {
                    throw new RuntimeException("PDF 파일이 너무 큽니다 (최대 50MB).");
                }
                out.write(buffer, 0, bytesRead);
            }
            return out.toByteArray();
        }
    }

    /**
     * PDF 바이트 배열을 HTML 문자열로 변환합니다.
     * 각 페이지를 <div class="pdf-page"> 블록으로 감쌉니다.
     */
    private String convertPdfToHtml(byte[] pdfBytes, String sourceUrl) throws Exception {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            int totalPages = doc.getNumberOfPages();
            log.info("PDF 페이지 수: {}", totalPages);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            StringBuilder htmlBody = new StringBuilder();

            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(doc);

                if (pageText == null || pageText.trim().isEmpty()) {
                    continue;
                }

                htmlBody.append("<div class=\"pdf-page\" data-page=\"")
                        .append(page)
                        .append("\">\n");

                // 페이지 헤더 (2페이지 이상일 때만)
                if (totalPages > 1) {
                    htmlBody.append("<p class=\"pdf-page-number\" style=\"color:#999;font-size:12px;margin-bottom:8px;\">")
                            .append("— ").append(page).append(" / ").append(totalPages).append(" —")
                            .append("</p>\n");
                }

                // 줄바꿈 기준으로 문단 분리
                String[] lines = pageText.split("\\r?\\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    htmlBody.append("<p>")
                            .append(escapeHtml(trimmed))
                            .append("</p>\n");
                }

                htmlBody.append("</div>\n");
            }

            if (htmlBody.length() == 0) {
                htmlBody.append("<div class=\"pdf-page\"><p>텍스트를 추출할 수 없는 PDF입니다. (이미지 기반 PDF이거나 보안이 적용된 파일일 수 있습니다.)</p></div>");
            }

            return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                    + "<style>"
                    + "body{font-family:system-ui,sans-serif;padding:24px;margin:0;background:#fff;color:#111;line-height:1.7;}"
                    + ".pdf-page{max-width:800px;margin:0 auto 32px;padding:24px;border:1px solid #e5e7eb;border-radius:8px;}"
                    + "p{margin:0 0 8px;word-break:break-word;}"
                    + "</style>"
                    + "<title>PDF: " + escapeHtml(sourceUrl) + "</title>"
                    + "</head><body>\n"
                    + htmlBody
                    + "</body></html>";
        }
    }

    /**
     * HTML 특수문자를 이스케이프합니다.
     */
    private String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * URL이 PDF 파일을 가리키는지 판별합니다.
     * 확장자 기반 1차 검사 + Content-Type 헤더 2차 검사를 수행합니다.
     */
    public boolean isPdfUrl(String url) {
        if (url == null || url.isBlank()) return false;

        String lower = url.toLowerCase();
        // 쿼리스트링/해시 제거 후 확장자 확인
        String path = lower.split("[?#]")[0];
        if (path.endsWith(".pdf")) {
            return true;
        }

        // 확장자로 판단하기 어려운 경우 HEAD 요청으로 Content-Type 확인
        try {
            URL u = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.connect();
            String contentType = conn.getContentType();
            conn.disconnect();
            if (contentType != null && contentType.toLowerCase().contains("application/pdf")) {
                log.info("Content-Type으로 PDF 감지: {}", url);
                return true;
            }
        } catch (Exception e) {
            log.debug("PDF HEAD 요청 실패 (무시): {}", e.getMessage());
        }

        return false;
    }
}
