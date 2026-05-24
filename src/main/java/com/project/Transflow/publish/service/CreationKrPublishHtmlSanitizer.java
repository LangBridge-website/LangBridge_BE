package com.project.Transflow.publish.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
public class CreationKrPublishHtmlSanitizer {

    private static final List<String> CONTENT_ROOT_SELECTORS = List.of(
            "#contentPost article",
            "#content article",
            "article",
            "main",
            "[role=main]"
    );

    private static final Set<String> REMOVED_TAGS = Set.of(
            "script", "style", "meta", "link", "head", "title", "noscript", "base", "template"
    );

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "p", "br", "hr",
            "h1", "h2", "h3", "h4", "h5", "h6",
            "ul", "ol", "li",
            "blockquote", "pre", "code",
            "strong", "b", "em", "i", "u", "s", "sub", "sup", "span",
            "a", "img",
            "table", "thead", "tbody", "tfoot", "tr", "th", "td",
            "figure", "figcaption", "div", "section", "iframe"
    );

    private static final Set<String> STRIP_ATTRIBUTES = Set.of(
            "contenteditable", "data-paragraph-id", "data-paragraph-index", "data-transflow-id",
            "data-component-editable", "data-mce-type", "data-mce-style", "data-mce-bogus",
            "class", "id", "style", "srcset", "sizes", "onclick", "onload", "onerror"
    );

    public String sanitize(String html) {
        return sanitize(html, null);
    }

    public String sanitize(String html, String baseUrl) {
        if (html == null || html.isBlank()) {
            return html;
        }

        try {
            Document doc = Jsoup.parse(html);
            Element root = selectContentRoot(doc);
            removeUnwantedNodes(root);
            unwrapDisallowedTags(root);
            normalizeAttributes(root, baseUrl);
            removeEmptyContainers(root);

            String result = root.html().trim();
            log.info("creation.kr HTML sanitize: {} -> {} chars", html.length(), result.length());
            return result;
        } catch (Exception e) {
            log.warn("HTML sanitize 실패, 원본 사용: {}", e.getMessage());
            return html;
        }
    }

    private Element selectContentRoot(Document doc) {
        for (String selector : CONTENT_ROOT_SELECTORS) {
            Element candidate = doc.selectFirst(selector);
            if (candidate != null && hasMeaningfulContent(candidate)) {
                return candidate;
            }
        }
        return doc.body();
    }

    private boolean hasMeaningfulContent(Element element) {
        String text = element.text();
        return (text != null && text.trim().length() > 20) || !element.select("img").isEmpty();
    }

    private void removeUnwantedNodes(Element root) {
        root.select(String.join(", ", REMOVED_TAGS)).remove();
        root.select("span.mce_SELRES_start, [data-mce-type=bookmark]").remove();
        root.select("iframe[data-disabled=true]").remove();

        Elements iframes = root.select("iframe");
        for (Element iframe : iframes) {
            if (iframe.attr("src").isBlank() && iframe.text().isBlank()) {
                iframe.remove();
            }
        }
    }

    private void unwrapDisallowedTags(Element root) {
        List<Element> elements = new ArrayList<>(root.getAllElements());
        for (int i = elements.size() - 1; i >= 0; i--) {
            Element element = elements.get(i);
            if (element == root) {
                continue;
            }
            String tag = element.tagName().toLowerCase(Locale.ROOT);
            if (REMOVED_TAGS.contains(tag)) {
                element.remove();
            } else if (!ALLOWED_TAGS.contains(tag)) {
                element.unwrap();
            }
        }
    }

    private void normalizeAttributes(Element root, String baseUrl) {
        for (Element element : root.getAllElements()) {
            List<String> attributeNames = new ArrayList<>();
            element.attributes().forEach(attr -> attributeNames.add(attr.getKey()));
            for (String attr : attributeNames) {
                if (STRIP_ATTRIBUTES.contains(attr) || attr.startsWith("data-")) {
                    element.removeAttr(attr);
                }
            }
        }

        for (Element img : root.select("img")) {
            String src = img.attr("src");
            if (!src.isBlank()) {
                img.attr("src", toAbsoluteUrl(src, baseUrl));
            }
        }

        for (Element anchor : root.select("a[href]")) {
            anchor.attr("href", toAbsoluteUrl(anchor.attr("href"), baseUrl));
        }
    }

    private void removeEmptyContainers(Element root) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Element element : new ArrayList<>(root.select("div, section, span"))) {
                if (element == root) {
                    continue;
                }
                if (isEmptyContainer(element)) {
                    element.remove();
                    changed = true;
                }
            }
        }
    }

    private boolean isEmptyContainer(Element element) {
        if (!element.text().trim().isEmpty()) {
            return false;
        }
        return element.select("img, iframe, table, ul, ol, blockquote, pre, hr").isEmpty();
    }

    private String toAbsoluteUrl(String url, String baseUrl) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("//")) {
            return "https:" + trimmed;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return trimmed;
        }
        try {
            return URI.create(baseUrl).resolve(trimmed).toString();
        } catch (Exception e) {
            log.debug("URL 절대경로 변환 실패: {} (base: {})", trimmed, baseUrl);
            return trimmed;
        }
    }
}
