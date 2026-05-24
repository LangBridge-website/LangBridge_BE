package com.project.Transflow.document.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * 프론트 countParagraphs와 동일한 규칙으로 HTML 문단 수를 계산합니다.
 */
public final class ParagraphCounter {

    private ParagraphCounter() {
    }

    public static int countParagraphs(String html) {
        if (html == null || html.trim().isEmpty()) {
            return 0;
        }
        try {
            Document doc = Jsoup.parse(html);
            Element body = doc.body();
            if (body == null) {
                return 0;
            }

            Elements indexed = body.select("[data-paragraph-index]");
            if (!indexed.isEmpty()) {
                int maxIndex = -1;
                for (Element el : indexed) {
                    String indexStr = el.attr("data-paragraph-index");
                    if (indexStr == null || indexStr.isEmpty()) {
                        continue;
                    }
                    try {
                        int index = Integer.parseInt(indexStr.trim());
                        if (index > maxIndex) {
                            maxIndex = index;
                        }
                    } catch (NumberFormatException ignored) {
                        // skip invalid index
                    }
                }
                return maxIndex + 1;
            }

            Elements elements = body.select("p, h1, h2, h3, h4, h5, h6, div, li, blockquote, article, section, figure, figcaption");
            int count = 0;
            for (Element el : elements) {
                String text = el.text() != null ? el.text().trim() : "";
                boolean hasImages = !el.select("img").isEmpty();
                if ((!text.isEmpty()) || hasImages) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }
}
