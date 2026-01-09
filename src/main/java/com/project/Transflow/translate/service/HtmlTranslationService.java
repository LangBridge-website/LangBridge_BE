package com.project.Transflow.translate.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * HTML 구조를 유지하면서 텍스트만 번역하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HtmlTranslationService {

    private final TranslationService translationService;

    // 번역하지 않아야 할 태그들
    private static final List<String> SKIP_TAGS = List.of(
            "script", "style", "noscript", "code", "pre"
    );


    /**
     * HTML을 파싱하여 텍스트 노드만 번역하고 HTML 구조는 유지
     * 
     * @param html 원본 HTML
     * @param targetLang 대상 언어
     * @param sourceLang 원본 언어 (null이면 자동 감지)
     * @return 번역된 HTML
     */
    public String translateHtml(String html, String targetLang, String sourceLang) {
        try {
            log.info("HTML 번역 시작 - Target: {}, HTML 길이: {}", targetLang, html.length());

            Document doc = Jsoup.parse(html);
            
            // JavaScript 완전 제거 (번역 전에 제거하여 동적 콘텐츠 변경 방지)
            // 1. 모든 script 태그 제거 (인라인, 외부 모두) - 여러 번 제거하여 확실히
            doc.select("script").remove();
            doc.select("noscript").remove();
            // 혹시 모를 경우를 위해 다시 한 번
            doc.select("script").remove();
            
            // 2. 외부 스크립트 파일 링크 제거
            doc.select("link[rel='preload'][as='script']").remove();
            doc.select("link[rel='modulepreload']").remove();
            // type이 module인 스크립트도 제거
            doc.select("script[type='module']").remove();
            doc.select("script[type='text/javascript']").remove();
            
            // 3. 이벤트 핸들러 속성 제거 (onclick, onload 등)
            doc.select("*").forEach(element -> {
                java.util.List<org.jsoup.nodes.Attribute> attrsToRemove = new java.util.ArrayList<>();
                element.attributes().forEach(attr -> {
                    String key = attr.getKey().toLowerCase();
                    // 이벤트 핸들러 제거
                    if (key.startsWith("on")) {
                        attrsToRemove.add(attr);
                    }
                });
                attrsToRemove.forEach(attr -> element.removeAttr(attr.getKey()));
            });
            
            // 4. React나 다른 프레임워크가 실행되지 않도록 제거
            // React는 data-reactroot 등을 사용하므로 제거
            doc.select("[data-reactroot]").removeAttr("data-reactroot");
            doc.select("[data-react-helmet]").removeAttr("data-react-helmet");
            // React 컴포넌트 마운트 지점 제거
            doc.select("#root, #app, [id^='react'], [class^='react']").forEach(element -> {
                // React 관련 속성 제거
                element.removeAttr("data-reactroot");
                element.removeAttr("data-react-helmet");
            });
            
            // 5. 외부 API 호출을 하는 요소 제거 또는 비활성화
            // iframe 제거 (다른 페이지 로드 방지)
            doc.select("iframe[src]").forEach(iframe -> {
                iframe.removeAttr("src");
                iframe.attr("data-disabled", "true");
            });
            
            // 6. manifest.json 링크 제거 (로컬 파일 로드 방지)
            doc.select("link[rel='manifest']").remove();
            
            // 번역할 텍스트 노드들을 수집
            List<TranslatableText> translatableTexts = collectTranslatableTexts(doc);

            log.info("번역 가능한 텍스트 노드 수: {}", translatableTexts.size());

            // 텍스트들을 배치로 번역 (DeepL API 효율성 고려)
            translateTextNodes(translatableTexts, targetLang, sourceLang);

            // 번역된 텍스트로 HTML 재구성 전에 한 번 더 스크립트 제거 (혹시 모를 경우 대비)
            doc.select("script").remove();
            doc.select("noscript").remove();
            
            // React나 다른 프레임워크의 초기화 코드가 있는지 확인하고 제거
            // window 객체를 사용하는 인라인 코드 제거를 위해 <script> 태그는 이미 제거됨
            
            // 번역된 텍스트로 HTML 재구성
            String translatedHtml = doc.html();
            
            // 디버깅: 번역이 제대로 적용되었는지 확인
            // 원본 텍스트가 남아있는지 체크 (일부만)
            int originalTextCount = 0;
            for (TranslatableText tt : translatableTexts) {
                String currentText = tt.textNode.text().trim();
                if (currentText.equals(tt.originalText)) {
                    originalTextCount++;
                }
            }
            if (originalTextCount > 0) {
                log.warn("번역이 적용되지 않은 텍스트 노드가 {}개 있습니다.", originalTextCount);
            }

            log.info("HTML 번역 완료");
            return translatedHtml;

        } catch (Exception e) {
            log.error("HTML 번역 실패", e);
            throw new RuntimeException("HTML 번역 중 오류 발생: " + e.getMessage());
        }
    }

    /**
     * 번역 가능한 텍스트 노드들을 수집
     */
    private List<TranslatableText> collectTranslatableTexts(Document doc) {
        List<TranslatableText> texts = new ArrayList<>();

        // 모든 텍스트 노드를 순회
        doc.traverse(new org.jsoup.select.NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode) {
                    TextNode textNode = (TextNode) node;
                    String text = textNode.text().trim();
                    
                    // 빈 텍스트나 공백만 있는 경우 스킵
                    if (text.isEmpty() || text.length() < 2) {
                        return;
                    }

                    // 부모 태그 확인
                    Node parentNode = textNode.parent();
                    if (parentNode instanceof Element) {
                        Element parent = (Element) parentNode;
                        if (SKIP_TAGS.contains(parent.tagName().toLowerCase())) {
                            return;
                        }
                    }

                    // 특정 패턴 스킵 (URL, 이메일, 숫자만 있는 경우 등)
                    if (shouldSkipText(text)) {
                        return;
                    }

                    texts.add(new TranslatableText(textNode, text));
                }
            }

            @Override
            public void tail(Node node, int depth) {
                // 필요 없음
            }
        });

        return texts;
    }

    /**
     * 번역하지 않아야 할 텍스트인지 확인
     */
    private boolean shouldSkipText(String text) {
        // URL 패턴
        if (Pattern.matches("^https?://.*", text)) {
            return true;
        }
        
        // 이메일 패턴
        if (Pattern.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$", text)) {
            return true;
        }
        
        // 숫자만 있는 경우
        if (Pattern.matches("^\\d+$", text)) {
            return true;
        }
        
        // 공백이나 특수문자만 있는 경우
        if (Pattern.matches("^[\\s\\p{Punct}]+$", text)) {
            return true;
        }

        return false;
    }

    /**
     * 텍스트 노드들을 번역
     * 문맥을 고려한 배치 번역으로 품질과 속도 모두 개선
     */
    private void translateTextNodes(List<TranslatableText> texts, String targetLang, String sourceLang) {
        if (texts.isEmpty()) {
            return;
        }

        log.info("번역 시작 - 총 {}개 텍스트 노드", texts.size());
        
        // 문맥을 고려한 그룹화: 같은 부모 요소(문단, 섹션 등)의 텍스트를 함께 번역
        List<List<TranslatableText>> contextGroups = groupByContext(texts);
        log.info("문맥 그룹 수: {}개", contextGroups.size());
        
        int totalBatches = 0;
        int currentBatchNumber = 0;
        
        // 각 문맥 그룹을 처리
        for (List<TranslatableText> contextGroup : contextGroups) {
            // 문맥 그룹의 모든 텍스트를 합쳐서 하나의 문장/문단으로 번역
            // 이렇게 하면 문맥이 유지되어 번역 품질이 향상됨
            if (contextGroup.isEmpty()) {
                continue;
            }
            
            // 같은 문맥 그룹의 모든 텍스트를 합치기
            StringBuilder combinedText = new StringBuilder();
            for (int i = 0; i < contextGroup.size(); i++) {
                TranslatableText tt = contextGroup.get(i);
                String text = tt.originalText;
                
                // 텍스트 사이에 공백 추가 (단, 이미 공백으로 시작/끝나면 제외)
                if (i > 0 && !text.startsWith(" ") && !combinedText.toString().endsWith(" ")) {
                    combinedText.append(" ");
                }
                combinedText.append(text);
            }
            
            String fullText = combinedText.toString().trim();
            
            // 빈 텍스트 체크
            if (fullText.isEmpty()) {
                log.debug("빈 텍스트 그룹 스킵");
                continue;
            }
            
            // 합쳐진 텍스트를 번역
            try {
                String translatedText = translationService.translate(fullText, targetLang, sourceLang);
                
                // 번역된 텍스트를 원래 텍스트 노드들에 분배
                // 원본 텍스트의 비율에 따라 번역된 텍스트를 분배
                distributeTranslatedText(contextGroup, fullText, translatedText);
                
                currentBatchNumber++;
                totalBatches++;
                log.info("문맥 그룹 번역 완료 ({}, {}개 텍스트 노드)", 
                        currentBatchNumber, contextGroup.size());
                
            } catch (Exception e) {
                log.error("문맥 그룹 번역 실패: {}", e.getMessage());
                // 실패 시 개별 번역으로 폴백
                for (TranslatableText tt : contextGroup) {
                    try {
                        String translated = translationService.translate(tt.originalText, targetLang, sourceLang);
                        tt.textNode.text(translated.trim());
                    } catch (Exception ex) {
                        log.warn("개별 번역 실패: {}", ex.getMessage());
                    }
                }
            }
            
            // 배치 간 짧은 대기 (50ms)
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("배치 간 대기 중 인터럽트 발생");
            }
        }
        
        log.info("모든 텍스트 노드 번역 완료! (총 {}개 배치)", totalBatches);
    }
    
    /**
     * 번역된 텍스트를 원래 텍스트 노드들에 분배
     * 원본 텍스트의 비율에 따라 번역된 텍스트를 분배하여 HTML 구조 유지
     */
    private void distributeTranslatedText(List<TranslatableText> textNodes, String originalFullText, String translatedFullText) {
        if (textNodes.size() == 1) {
            // 텍스트 노드가 하나면 그대로 적용
            textNodes.get(0).textNode.text(translatedFullText.trim());
            return;
        }
        
        // 원본 텍스트의 각 부분의 길이 비율 계산
        int totalLength = originalFullText.length();
        if (totalLength == 0) {
            return;
        }
        
        // 각 텍스트 노드의 원본 텍스트 길이 비율 계산
        List<Double> ratios = new ArrayList<>();
        for (TranslatableText tt : textNodes) {
            double ratio = (double) tt.originalText.length() / totalLength;
            ratios.add(ratio);
        }
        
        // 번역된 텍스트를 비율에 따라 분배
        int translatedLength = translatedFullText.length();
        int currentPos = 0;
        
        for (int i = 0; i < textNodes.size(); i++) {
            TranslatableText tt = textNodes.get(i);
            double ratio = ratios.get(i);
            int segmentLength = (int) (translatedLength * ratio);
            
            // 마지막 세그먼트는 남은 모든 텍스트 사용
            if (i == textNodes.size() - 1) {
                String remainingText = translatedFullText.substring(currentPos).trim();
                tt.textNode.text(remainingText.isEmpty() ? translatedFullText.substring(currentPos) : remainingText);
            } else {
                int endPos = Math.min(currentPos + segmentLength, translatedLength);
                String segmentText = translatedFullText.substring(currentPos, endPos).trim();
                // 빈 텍스트가 되면 공백 하나만 유지
                tt.textNode.text(segmentText.isEmpty() ? " " : segmentText);
                currentPos = endPos;
            }
        }
    }
    
    /**
     * 문맥을 고려하여 텍스트 노드를 그룹화
     * 같은 부모 요소(문단, 섹션 등)의 텍스트를 합쳐서 하나의 문장/문단으로 번역
     */
    private List<List<TranslatableText>> groupByContext(List<TranslatableText> texts) {
        List<List<TranslatableText>> groups = new ArrayList<>();
        List<TranslatableText> currentGroup = new ArrayList<>();
        Element lastParent = null;
        
        for (TranslatableText translatableText : texts) {
            Node parentNode = translatableText.textNode.parent();
            Element currentParent = null;
            
            if (parentNode instanceof Element) {
                currentParent = (Element) parentNode;
                
                // 문단, 제목, 리스트 항목 등 문맥 단위 찾기
                while (currentParent != null) {
                    String tagName = currentParent.tagName().toLowerCase();
                    // 문맥 단위로 간주할 태그들
                    if (tagName.equals("p") || tagName.equals("h1") || tagName.equals("h2") || 
                        tagName.equals("h3") || tagName.equals("h4") || tagName.equals("h5") || 
                        tagName.equals("h6") || tagName.equals("li") || tagName.equals("td") || 
                        tagName.equals("th") || tagName.equals("blockquote") || 
                        tagName.equals("article") || tagName.equals("section") ||
                        tagName.equals("div") || tagName.equals("span")) {
                        break;
                    }
                    Node parent = currentParent.parent();
                    if (parent instanceof Element) {
                        currentParent = (Element) parent;
                    } else {
                        currentParent = null;
                    }
                }
            }
            
            // 부모가 변경되면 새 그룹 시작
            if (lastParent != null && !lastParent.equals(currentParent)) {
                if (!currentGroup.isEmpty()) {
                    groups.add(new ArrayList<>(currentGroup));
                    currentGroup.clear();
                }
            }
            
            currentGroup.add(translatableText);
            lastParent = currentParent;
        }
        
        // 마지막 그룹 추가
        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }
        
        return groups;
    }
    
    /**
     * 최적화된 배치 번역 처리
     * 여러 텍스트를 한 번에 번역하여 API 호출 횟수 대폭 감소
     */
    private void translateBatchOptimized(List<TranslatableText> batch, List<String> batchTexts, 
                                         String targetLang, String sourceLang, 
                                         int batchNumber, int totalBatches) {
        int successCount = 0;
        int failCount = 0;
        
        try {
            // 여러 텍스트를 한 번에 번역 (DeepL API는 여러 텍스트를 지원)
            List<String> translatedTexts = translationService.translateBatch(batchTexts, targetLang, sourceLang);
            
            // 번역 결과를 각 텍스트 노드에 매핑
            for (int i = 0; i < batch.size() && i < translatedTexts.size(); i++) {
                TranslatableText translatableText = batch.get(i);
                String translated = translatedTexts.get(i);
                
                if (translated != null && !translated.trim().isEmpty() && 
                    !translated.trim().equals(translatableText.originalText)) {
                    translatableText.textNode.text(translated.trim());
                    successCount++;
                } else {
                    log.warn("번역 결과가 원본과 동일하거나 비어있음: 원본='{}'", 
                            translatableText.originalText.substring(0, Math.min(50, translatableText.originalText.length())));
                    failCount++;
                }
            }
            
            // 번역 결과가 부족한 경우 (일부 실패)
            if (translatedTexts.size() < batch.size()) {
                log.warn("배치 {}: 일부 번역 실패 (요청: {}, 응답: {})", 
                        batchNumber, batch.size(), translatedTexts.size());
                failCount += (batch.size() - translatedTexts.size());
            }
            
        } catch (Exception e) {
            log.error("배치 {} 번역 실패: {}. 개별 번역으로 폴백", batchNumber, e.getMessage());
            
            // 배치 번역 실패 시 개별 번역으로 폴백 (하위 호환성)
            for (TranslatableText translatableText : batch) {
                try {
                    String translated = translationService.translate(
                            translatableText.originalText, 
                            targetLang, 
                            sourceLang
                    );
                    if (translated != null && !translated.trim().isEmpty()) {
                        translatableText.textNode.text(translated.trim());
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception ex) {
                    log.warn("개별 번역 실패: {}", ex.getMessage());
                    failCount++;
                }
            }
        }
        
        log.info("배치 {} 완료: 성공 {}, 실패 {}", batchNumber, successCount, failCount);
    }


    /**
     * 번역 가능한 텍스트 노드를 나타내는 내부 클래스
     */
    private static class TranslatableText {
        final TextNode textNode;
        final String originalText;

        TranslatableText(TextNode textNode, String originalText) {
            this.textNode = textNode;
            this.originalText = originalText;
        }
    }
}

