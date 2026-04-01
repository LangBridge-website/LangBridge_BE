package com.project.Transflow.translate.service;

import com.project.Transflow.term.entity.TermDictionary;
import com.project.Transflow.term.repository.TermDictionaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 번역 완료된 HTML에서 용어집(targetTerm)에 해당하는 단어를 찾아
 * 컬러 span으로 감싸고 원어(sourceTerm)를 괄호 안에 표기합니다.
 *
 * <p>결과 예시:
 * <pre>
 *   "이 문서는 유엔(United Nations)에서 발행했습니다."
 *   &lt;span class="glossary-term" data-original="United Nations"&gt;유엔(United Nations)&lt;/span&gt;
 * </pre>
 *
 * <p>기존 undo/redo 호환성: span 태그가 HTML 자체에 포함되어 저장되므로,
 * TranslationWork의 undo/redo HTML 스냅샷 방식과 완전히 호환됩니다.
 * 번역자가 편집 시 span을 그대로 수정·삭제할 수 있습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlossaryHighlightService {

    private final TermDictionaryRepository termDictionaryRepository;

    /** 번역 대상이 아닌 HTML 태그 이름 */
    private static final List<String> SKIP_TAGS = List.of(
            "script", "style", "noscript", "code", "pre", "textarea"
    );

    /**
     * 번역 완료된 HTML에 용어집 하이라이트를 적용합니다.
     *
     * @param translatedHtml 번역된 HTML 문자열 (AI_DRAFT)
     * @param sourceLang     원문 언어 코드 (예: "EN")
     * @param targetLang     번역 언어 코드 (예: "KO")
     * @return 용어 하이라이트가 적용된 HTML 문자열
     */
    public String annotate(String translatedHtml, String sourceLang, String targetLang) {
        if (translatedHtml == null || translatedHtml.isBlank()) {
            return translatedHtml;
        }
        if (sourceLang == null || targetLang == null) {
            return translatedHtml;
        }

        String normalizedSource = normalizeLanguage(sourceLang);
        String normalizedTarget = normalizeLanguage(targetLang);

        List<TermDictionary> terms = termDictionaryRepository
                .findBySourceLangAndTargetLang(normalizedSource, normalizedTarget);

        if (terms.isEmpty()) {
            log.debug("용어집 없음 ({} → {}), 하이라이트 생략", normalizedSource, normalizedTarget);
            return translatedHtml;
        }

        // 긴 targetTerm을 먼저 매칭 (부분 매칭 오류 방지)
        terms.sort(Comparator.comparingInt((TermDictionary t) -> t.getTargetTerm().length()).reversed());

        log.info("용어집 하이라이트 시작 ({} → {}): {}개 용어", normalizedSource, normalizedTarget, terms.size());

        Document doc = Jsoup.parse(translatedHtml);

        // 텍스트 노드를 수집 (순회 중 수정하면 ConcurrentModification 발생하므로 먼저 수집)
        List<TextNode> textNodes = collectTextNodes(doc);

        int annotatedCount = 0;
        for (TextNode textNode : textNodes) {
            String originalText = textNode.text();
            if (originalText.trim().isEmpty()) continue;

            String highlighted = buildHighlightedHtml(originalText, terms);
            if (highlighted == null) continue; // 매칭된 용어 없음

            // 기존 textNode를 highlighted HTML로 교체
            // Jsoup Node.before(html) + remove() 를 사용
            textNode.before(highlighted);
            textNode.remove();
            annotatedCount++;
        }

        log.info("용어집 하이라이트 완료: {}개 텍스트 노드 수정", annotatedCount);
        return doc.outerHtml();
    }

    /**
     * 주어진 텍스트에서 용어집 targetTerm이 발견되면 span으로 감싼 HTML 문자열을 반환합니다.
     * 매칭되는 용어가 없으면 null을 반환합니다.
     */
    private String buildHighlightedHtml(String text, List<TermDictionary> terms) {
        boolean anyMatch = false;

        // 매칭 위치 목록 수집 (겹침 방지)
        // entry: [startIdx, endIdx, sourceTerm, targetTerm]
        List<int[]> matches = new ArrayList<>();

        for (TermDictionary term : terms) {
            String target = term.getTargetTerm();
            String source = term.getSourceTerm();
            if (target == null || target.isBlank() || source == null || source.isBlank()) continue;

            // 대소문자 무시 패턴, 단어 경계 포함 (알파벳 기반 언어 대응)
            String regex = "(?i)" + Pattern.quote(target);
            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(text);

            while (m.find()) {
                int start = m.start();
                int end = m.end();
                // 기존 매칭과 겹치는지 확인
                boolean overlaps = false;
                for (int[] existing : matches) {
                    if (start < existing[1] && end > existing[0]) {
                        overlaps = true;
                        break;
                    }
                }
                if (!overlaps) {
                    matches.add(new int[]{start, end});
                    // sourceTerm과 targetTerm 인덱스를 추가로 저장하기 위해 별도 리스트 사용
                    anyMatch = true;
                }
            }
        }

        if (!anyMatch) return null;

        // 매칭 위치와 원래 용어 정보를 함께 보관하기 위해 MatchInfo 사용
        List<MatchInfo> matchInfos = new ArrayList<>();
        for (TermDictionary term : terms) {
            String target = term.getTargetTerm();
            String source = term.getSourceTerm();
            if (target == null || target.isBlank() || source == null || source.isBlank()) continue;

            String regex = "(?i)" + Pattern.quote(target);
            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(text);

            while (m.find()) {
                int start = m.start();
                int end = m.end();
                boolean overlaps = matchInfos.stream()
                        .anyMatch(mi -> start < mi.end && end > mi.start);
                if (!overlaps) {
                    matchInfos.add(new MatchInfo(start, end, source, text.substring(start, end)));
                }
            }
        }

        if (matchInfos.isEmpty()) return null;

        // 위치 오름차순 정렬
        matchInfos.sort(Comparator.comparingInt(mi -> mi.start));

        // HTML 조립
        StringBuilder sb = new StringBuilder();
        int cursor = 0;

        for (MatchInfo mi : matchInfos) {
            if (mi.start > cursor) {
                sb.append(escapeHtml(text.substring(cursor, mi.start)));
            }
            // span 태그로 감싸기: targetTerm(sourceTerm)
            sb.append("<span class=\"glossary-term\" data-original=\"")
              .append(escapeHtml(mi.sourceTerm))
              .append("\">")
              .append(escapeHtml(mi.matchedText))
              .append("(").append(escapeHtml(mi.sourceTerm)).append(")")
              .append("</span>");
            cursor = mi.end;
        }

        if (cursor < text.length()) {
            sb.append(escapeHtml(text.substring(cursor)));
        }

        return sb.toString();
    }

    /**
     * 번역 가능한 텍스트 노드를 문서에서 수집합니다.
     * script, style, pre, code 등 내부 텍스트 노드는 제외합니다.
     */
    private List<TextNode> collectTextNodes(Document doc) {
        List<TextNode> result = new ArrayList<>();
        doc.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (!(node instanceof TextNode)) return;
                TextNode tn = (TextNode) node;
                if (tn.text().trim().isEmpty()) return;

                Node parent = tn.parent();
                while (parent != null) {
                    if (parent instanceof org.jsoup.nodes.Element) {
                        String tag = ((org.jsoup.nodes.Element) parent).tagName().toLowerCase();
                        if (SKIP_TAGS.contains(tag)) return;
                    }
                    parent = parent.parent();
                }
                result.add(tn);
            }

            @Override
            public void tail(Node node, int depth) {
            }
        });
        return result;
    }

    private String normalizeLanguage(String lang) {
        if (lang == null) return null;
        return lang.trim().toUpperCase().split("-")[0];
    }

    private String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** 매칭된 단어 정보를 담는 내부 클래스 */
    private static class MatchInfo {
        final int start;
        final int end;
        final String sourceTerm;
        final String matchedText;

        MatchInfo(int start, int end, String sourceTerm, String matchedText) {
            this.start = start;
            this.end = end;
            this.sourceTerm = sourceTerm;
            this.matchedText = matchedText;
        }
    }
}
