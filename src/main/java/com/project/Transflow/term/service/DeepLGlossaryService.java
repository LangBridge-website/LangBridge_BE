package com.project.Transflow.term.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DeepLGlossaryService {

    private final WebClient webClient;
    private final String apiKey;

    public DeepLGlossaryService(
            @Value("${deepl.api.url}") String apiUrl,
            @Value("${deepl.api.key}") String apiKey) {
        this.apiKey = apiKey;
        
        // 번역 API URL에서 /v2/translate 제거하여 base URL만 사용
        // 번역 API: https://api-free.deepl.com/v2/translate
        // Glossary API: https://api-free.deepl.com/v3/glossaries
        String baseUrl = apiUrl;
        if (baseUrl.endsWith("/v2/translate")) {
            baseUrl = baseUrl.replace("/v2/translate", "");
        }
        
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }

    /**
     * DeepL Glossary 생성
     * @param glossaryName Glossary 이름
     * @param sourceLang 원문 언어 코드 (소문자로 변환됨)
     * @param targetLang 번역 언어 코드 (소문자로 변환됨)
     * @param entries 용어 목록
     * @return 생성된 Glossary ID
     */
    public String createGlossary(String glossaryName, String sourceLang, String targetLang, 
                                 List<TermEntry> entries) {
        try {
            // TSV 형식으로 entries 변환
            String entriesTsv = entries.stream()
                    .map(e -> escapeTsv(e.getSourceTerm()) + "\t" + escapeTsv(e.getTargetTerm()))
                    .collect(Collectors.joining("\n"));

            // DeepL 언어 코드 변환 (EN -> en, KO -> ko)
            String deeplSourceLang = convertToDeepLLangCode(sourceLang);
            String deeplTargetLang = convertToDeepLLangCode(targetLang);

            CreateGlossaryRequest request = new CreateGlossaryRequest();
            request.setName(glossaryName);
            request.setDictionaries(List.of(
                    new DictionaryRequest(deeplSourceLang, deeplTargetLang, entriesTsv, "tsv")
            ));

            CreateGlossaryResponse response = webClient.post()
                    .uri("/v3/glossaries")
                    .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(CreateGlossaryResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response != null && response.getGlossaryId() != null) {
                log.info("DeepL Glossary 생성 성공: glossaryId={}, name={}, entries={}", 
                        response.getGlossaryId(), response.getName(), entries.size());
                return response.getGlossaryId();
            }

            throw new RuntimeException("DeepL Glossary 생성 실패: 응답이 비어있습니다.");

        } catch (WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("DeepL Glossary 생성 실패: status={}, body={}", e.getStatusCode(), responseBody, e);
            throw new RuntimeException("DeepL Glossary 생성 중 오류가 발생했습니다: " + e.getMessage() + 
                    (responseBody != null ? " (응답: " + responseBody + ")" : ""), e);
        } catch (Exception e) {
            log.error("DeepL Glossary 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("DeepL Glossary 생성 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * DeepL Glossary의 Dictionary 교체 (PUT)
     * @param glossaryId Glossary ID
     * @param sourceLang 원문 언어 코드
     * @param targetLang 번역 언어 코드
     * @param entries 용어 목록
     */
    public void updateGlossaryDictionary(String glossaryId, String sourceLang, String targetLang, 
                                        List<TermEntry> entries) {
        try {
            // TSV 형식으로 entries 변환
            String entriesTsv = entries.stream()
                    .map(e -> escapeTsv(e.getSourceTerm()) + "\t" + escapeTsv(e.getTargetTerm()))
                    .collect(Collectors.joining("\n"));

            // DeepL 언어 코드 변환
            String deeplSourceLang = convertToDeepLLangCode(sourceLang);
            String deeplTargetLang = convertToDeepLLangCode(targetLang);

            DictionaryUpdateRequest request = new DictionaryUpdateRequest();
            request.setSourceLang(deeplSourceLang);
            request.setTargetLang(deeplTargetLang);
            request.setEntries(entriesTsv);
            request.setEntriesFormat("tsv");

            DictionaryUpdateResponse response = webClient.put()
                    .uri("/v3/glossaries/{glossaryId}/dictionaries", glossaryId)
                    .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(DictionaryUpdateResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response != null) {
                log.info("DeepL Glossary Dictionary 업데이트 성공: glossaryId={}, entries={}", 
                        glossaryId, entries.size());
            } else {
                throw new RuntimeException("DeepL Glossary Dictionary 업데이트 실패: 응답이 비어있습니다.");
            }

        } catch (WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("DeepL Glossary Dictionary 업데이트 실패: status={}, body={}", e.getStatusCode(), responseBody, e);
            throw new RuntimeException("DeepL Glossary Dictionary 업데이트 중 오류가 발생했습니다: " + e.getMessage() + 
                    (responseBody != null ? " (응답: " + responseBody + ")" : ""), e);
        } catch (Exception e) {
            log.error("DeepL Glossary Dictionary 업데이트 실패: {}", e.getMessage(), e);
            throw new RuntimeException("DeepL Glossary Dictionary 업데이트 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * DeepL Glossary 삭제
     * @param glossaryId Glossary ID
     */
    public void deleteGlossary(String glossaryId) {
        try {
            webClient.delete()
                    .uri("/v3/glossaries/{glossaryId}", glossaryId)
                    .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(30))
                    .block();

            log.info("DeepL Glossary 삭제 성공: glossaryId={}", glossaryId);

        } catch (WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("DeepL Glossary 삭제 실패: status={}, body={}", e.getStatusCode(), responseBody, e);
            throw new RuntimeException("DeepL Glossary 삭제 중 오류가 발생했습니다: " + e.getMessage() + 
                    (responseBody != null ? " (응답: " + responseBody + ")" : ""), e);
        } catch (Exception e) {
            log.error("DeepL Glossary 삭제 실패: {}", e.getMessage(), e);
            throw new RuntimeException("DeepL Glossary 삭제 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * DeepL Glossary 조회
     * @param glossaryId Glossary ID
     * @return Glossary 정보
     */
    public CreateGlossaryResponse getGlossary(String glossaryId) {
        try {
            CreateGlossaryResponse response = webClient.get()
                    .uri("/v3/glossaries/{glossaryId}", glossaryId)
                    .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                    .retrieve()
                    .bodyToMono(CreateGlossaryResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            return response;

        } catch (WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("DeepL Glossary 조회 실패: status={}, body={}", e.getStatusCode(), responseBody, e);
            throw new RuntimeException("DeepL Glossary 조회 중 오류가 발생했습니다: " + e.getMessage() + 
                    (responseBody != null ? " (응답: " + responseBody + ")" : ""), e);
        } catch (Exception e) {
            log.error("DeepL Glossary 조회 실패: {}", e.getMessage(), e);
            throw new RuntimeException("DeepL Glossary 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 언어 코드 변환 (EN -> en, KO -> ko)
     */
    private String convertToDeepLLangCode(String langCode) {
        if (langCode == null || langCode.isEmpty()) {
            return "en";
        }
        return langCode.toLowerCase();
    }

    /**
     * TSV 형식에서 특수문자 이스케이프
     */
    private String escapeTsv(String text) {
        if (text == null) {
            return "";
        }
        // TSV에서 탭과 개행문자는 이스케이프 필요
        return text.replace("\t", " ").replace("\n", " ").replace("\r", " ");
    }

    // DTO 클래스들
    @Data
    public static class CreateGlossaryRequest {
        private String name;
        private List<DictionaryRequest> dictionaries;
    }

    @Data
    public static class DictionaryRequest {
        @JsonProperty("source_lang")
        private String sourceLang;
        @JsonProperty("target_lang")
        private String targetLang;
        private String entries;
        @JsonProperty("entries_format")
        private String entriesFormat;

        public DictionaryRequest(String sourceLang, String targetLang, String entries, String entriesFormat) {
            this.sourceLang = sourceLang;
            this.targetLang = targetLang;
            this.entries = entries;
            this.entriesFormat = entriesFormat;
        }
    }

    @Data
    public static class CreateGlossaryResponse {
        @JsonProperty("glossary_id")
        private String glossaryId;
        private String name;
        private List<DictionaryResponse> dictionaries;
        @JsonProperty("creation_time")
        private String creationTime;
    }

    @Data
    public static class DictionaryResponse {
        @JsonProperty("source_lang")
        private String sourceLang;
        @JsonProperty("target_lang")
        private String targetLang;
        @JsonProperty("entry_count")
        private Integer entryCount;
    }

    @Data
    public static class DictionaryUpdateRequest {
        @JsonProperty("source_lang")
        private String sourceLang;
        @JsonProperty("target_lang")
        private String targetLang;
        private String entries;
        @JsonProperty("entries_format")
        private String entriesFormat;
    }

    @Data
    public static class DictionaryUpdateResponse {
        @JsonProperty("source_lang")
        private String sourceLang;
        @JsonProperty("target_lang")
        private String targetLang;
        @JsonProperty("entry_count")
        private Integer entryCount;
    }

    @Data
    public static class TermEntry {
        private String sourceTerm;
        private String targetTerm;

        public TermEntry(String sourceTerm, String targetTerm) {
            this.sourceTerm = sourceTerm;
            this.targetTerm = targetTerm;
        }
    }
}

