package com.project.Transflow.translate.service;



import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CrawlerService {

    private static final int TIMEOUT = 10000; // 10 seconds

    public String crawlWebPage(String url) {
        try {
            log.info("크롤링 시작: {}", url);

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(TIMEOUT)
                    .get();

            // body의 텍스트만 추출 (스크립트, 스타일 제외)
            doc.select("script, style").remove();
            String text = doc.body().text();

            log.info("크롤링 완료. 텍스트 길이: {}", text.length());
            return text;

        } catch (Exception e) {
            log.error("크롤링 실패: {}", url, e);
            throw new RuntimeException("웹페이지 크롤링 중 오류 발생: " + e.getMessage());
        }
    }
}