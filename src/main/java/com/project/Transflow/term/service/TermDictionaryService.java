package com.project.Transflow.term.service;

import com.project.Transflow.term.dto.BatchCreateTermRequest;
import com.project.Transflow.term.dto.CreateTermRequest;
import com.project.Transflow.term.dto.TermDictionaryPageResponse;
import com.project.Transflow.term.dto.TermDictionaryResponse;
import com.project.Transflow.term.dto.UpdateTermRequest;
import com.project.Transflow.term.entity.TermDictionary;
import com.project.Transflow.term.repository.TermDictionaryRepository;
import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TermDictionaryService {

    private final TermDictionaryRepository termDictionaryRepository;
    private final UserRepository userRepository;
    private final DeepLGlossaryService deepLGlossaryService;

    @Transactional
    public TermDictionaryResponse createTerm(CreateTermRequest request, Long createdById) {
        // 중복 체크
        if (termDictionaryRepository.existsBySourceTermAndSourceLangAndTargetLang(
                request.getSourceTerm(), request.getSourceLang(), request.getTargetLang())) {
            throw new IllegalArgumentException("이미 존재하는 용어입니다: " + request.getSourceTerm());
        }

        User createdBy = userRepository.findById(createdById)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + createdById));

        TermDictionary term = TermDictionary.builder()
                .sourceTerm(request.getSourceTerm())
                .targetTerm(request.getTargetTerm())
                .sourceLang(request.getSourceLang())
                .targetLang(request.getTargetLang())
                .description(request.getDescription())
                .category(request.getCategory())
                .articleTitle(request.getArticleTitle())
                .articleSource(request.getArticleSource())
                .articleLink(request.getArticleLink())
                .memo(request.getMemo())
                .createdBy(createdBy)
                .build();

        TermDictionary saved = termDictionaryRepository.save(term);
        log.info("용어 사전 추가: {} -> {} ({} -> {})", request.getSourceTerm(), request.getTargetTerm(), 
                request.getSourceLang(), request.getTargetLang());
        
        // DeepL Glossary 동기화
        try {
            syncGlossaryToDeepL(request.getSourceLang(), request.getTargetLang());
        } catch (Exception e) {
            log.warn("DeepL Glossary 동기화 실패 (용어는 저장됨): {}", e.getMessage());
            // 용어는 저장되었으므로 계속 진행
        }
        
        return toResponse(saved);
    }

    /**
     * 대량 용어 추가 (TSV 형식)
     * @param request 대량 추가 요청 (TSV 형식: 구분\t영어\t한국어\t기사제목\t출처\t기사링크\t메모)
     * @param createdById 생성자 ID
     * @return 대량 추가 결과 (성공/실패 개수 및 에러 목록)
     */
    @Transactional
    public BatchCreateTermResult createTermsBatch(BatchCreateTermRequest request, Long createdById) {
        User createdBy = userRepository.findById(createdById)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + createdById));

        String sourceLang = request.getSourceLang().toUpperCase();
        String targetLang = request.getTargetLang().toUpperCase();
        
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;
        
        // TSV 형식 파싱 (각 줄: 구분\t영어\t한국어\t기사제목\t출처\t기사링크\t메모)
        String[] lines = request.getTermsText().split("\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue; // 빈 줄 스킵
            }
            
            // 헤더 줄 체크 (첫 줄이 "구분", "영어", "한국어" 등을 포함하면 스킵)
            if (i == 0 && (line.contains("구분") || line.contains("영어") || line.contains("한국어") || 
                          line.contains("기사제목") || line.contains("출처") || line.contains("기사링크") || line.contains("메모"))) {
                continue; // 헤더 줄 스킵
            }
            
            try {
                // 탭으로 분리 (7개 컬럼: 구분, 영어, 한국어, 기사제목, 출처, 기사링크, 메모)
                // -1을 사용하여 빈 값도 포함
                String[] parts = line.split("\t", -1);
                
                if (parts.length < 2) {
                    errors.add(String.format("줄 %d: 형식이 잘못되었습니다 (최소 2개 컬럼 필요: 영어, 한국어)", i + 1));
                    failedCount++;
                    continue;
                }
                
                String category = (parts.length > 0 && !parts[0].trim().isEmpty()) ? parts[0].trim() : null;
                String sourceTerm = (parts.length > 1 && !parts[1].trim().isEmpty()) ? parts[1].trim() : null;
                String targetTerm = (parts.length > 2 && !parts[2].trim().isEmpty()) ? parts[2].trim() : null;
                String articleTitle = (parts.length > 3 && !parts[3].trim().isEmpty()) ? parts[3].trim() : null;
                String articleSource = (parts.length > 4 && !parts[4].trim().isEmpty()) ? parts[4].trim() : null;
                String articleLink = (parts.length > 5 && !parts[5].trim().isEmpty()) ? parts[5].trim() : null;
                String memo = (parts.length > 6 && !parts[6].trim().isEmpty()) ? parts[6].trim() : null;
                
                if (sourceTerm == null || sourceTerm.isEmpty() || 
                    targetTerm == null || targetTerm.isEmpty()) {
                    errors.add(String.format("줄 %d: 영어 또는 한국어가 비어있습니다", i + 1));
                    failedCount++;
                    continue;
                }
                
                // 중복 체크
                if (termDictionaryRepository.existsBySourceTermAndSourceLangAndTargetLang(
                        sourceTerm, sourceLang, targetLang)) {
                    errors.add(String.format("줄 %d: 이미 존재하는 용어입니다 (%s)", i + 1, sourceTerm));
                    failedCount++;
                    continue;
                }
                
                // 용어 생성 및 저장
                TermDictionary term = TermDictionary.builder()
                        .sourceTerm(sourceTerm)
                        .targetTerm(targetTerm)
                        .sourceLang(sourceLang)
                        .targetLang(targetLang)
                        .category(category)
                        .articleTitle(articleTitle)
                        .articleSource(articleSource)
                        .articleLink(articleLink)
                        .memo(memo)
                        .createdBy(createdBy)
                        .build();
                
                termDictionaryRepository.save(term);
                successCount++;
                log.debug("용어 추가: {} -> {} ({} -> {})", sourceTerm, targetTerm, sourceLang, targetLang);
                
            } catch (Exception e) {
                errors.add(String.format("줄 %d: %s", i + 1, e.getMessage()));
                failedCount++;
                log.warn("용어 추가 실패 (줄 {}): {}", i + 1, e.getMessage());
            }
        }
        
        log.info("대량 용어 추가 완료: 성공={}, 실패={} ({} -> {})", 
                successCount, failedCount, sourceLang, targetLang);
        
        // DeepL Glossary 동기화 (성공한 용어가 있을 때만)
        // DeepL에는 영어와 한국어만 전송 (sourceTerm, targetTerm만 사용)
        if (successCount > 0) {
            try {
                syncGlossaryToDeepL(sourceLang, targetLang);
            } catch (Exception e) {
                log.warn("DeepL Glossary 동기화 실패 (용어는 저장됨): {}", e.getMessage());
                // 용어는 저장되었으므로 계속 진행
            }
        }
        
        return new BatchCreateTermResult(successCount, failedCount, errors);
    }
    
    /**
     * 대량 추가 결과 DTO
     */
    public static class BatchCreateTermResult {
        private final int successCount;
        private final int failedCount;
        private final List<String> errors;
        
        public BatchCreateTermResult(int successCount, int failedCount, List<String> errors) {
            this.successCount = successCount;
            this.failedCount = failedCount;
            this.errors = errors;
        }
        
        public int getSuccessCount() {
            return successCount;
        }
        
        public int getFailedCount() {
            return failedCount;
        }
        
        public List<String> getErrors() {
            return errors;
        }
    }

    @Transactional(readOnly = true)
    public List<TermDictionaryResponse> findAll() {
        return termDictionaryRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 페이지네이션을 사용한 용어 목록 조회
     */
    @Transactional(readOnly = true)
    public TermDictionaryPageResponse findAllPaged(String sourceLang, String targetLang, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TermDictionary> termPage;

        if (sourceLang != null && targetLang != null) {
            termPage = termDictionaryRepository.findBySourceLangAndTargetLang(
                    sourceLang.toUpperCase(), targetLang.toUpperCase(), pageable);
        } else if (sourceLang != null) {
            termPage = termDictionaryRepository.findBySourceLang(sourceLang.toUpperCase(), pageable);
        } else if (targetLang != null) {
            termPage = termDictionaryRepository.findByTargetLang(targetLang.toUpperCase(), pageable);
        } else {
            termPage = termDictionaryRepository.findAll(pageable);
        }

        List<TermDictionaryResponse> content = termPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return TermDictionaryPageResponse.builder()
                .content(content)
                .page(termPage.getNumber())
                .size(termPage.getSize())
                .totalElements(termPage.getTotalElements())
                .totalPages(termPage.getTotalPages())
                .first(termPage.isFirst())
                .last(termPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TermDictionaryResponse> findByLanguages(String sourceLang, String targetLang) {
        return termDictionaryRepository.findBySourceLangAndTargetLang(sourceLang, targetLang).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TermDictionaryResponse> findBySourceLang(String sourceLang) {
        return termDictionaryRepository.findBySourceLang(sourceLang).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TermDictionaryResponse> findByTargetLang(String targetLang) {
        return termDictionaryRepository.findByTargetLang(targetLang).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<TermDictionaryResponse> findById(Long id) {
        return termDictionaryRepository.findById(id)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<TermDictionaryResponse> findBySourceTerm(String sourceTerm, String sourceLang, String targetLang) {
        return termDictionaryRepository.findBySourceTermAndSourceLangAndTargetLang(sourceTerm, sourceLang, targetLang)
                .map(this::toResponse);
    }

    /**
     * 언어 쌍으로 DeepL Glossary ID 조회
     * 번역 시 자동으로 용어집을 사용하기 위해 호출
     * @param sourceLang 원문 언어 코드 (대소문자 무관)
     * @param targetLang 번역 언어 코드 (대소문자 무관)
     * @return DeepL Glossary ID (용어가 없거나 Glossary가 없으면 null)
     */
    @Transactional(readOnly = true)
    public String getGlossaryIdByLanguages(String sourceLang, String targetLang) {
        // 언어 코드 정규화 (대문자로 변환, DB에는 대문자로 저장됨)
        String normalizedSourceLang = (sourceLang != null) ? sourceLang.toUpperCase() : null;
        String normalizedTargetLang = (targetLang != null) ? targetLang.toUpperCase() : null;
        
        if (normalizedSourceLang == null || normalizedTargetLang == null) {
            return null;
        }
        
        List<TermDictionary> terms = termDictionaryRepository
                .findBySourceLangAndTargetLang(normalizedSourceLang, normalizedTargetLang);
        
        if (terms.isEmpty()) {
            return null;
        }
        
        // 용어 중 하나에서 glossaryId 추출 (같은 언어 쌍은 같은 glossaryId를 가짐)
        return terms.stream()
                .map(TermDictionary::getDeeplGlossaryId)
                .filter(id -> id != null && !id.isEmpty())
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public TermDictionaryResponse updateTerm(Long id, UpdateTermRequest request) {
        TermDictionary term = termDictionaryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("용어를 찾을 수 없습니다: " + id));

        if (request.getSourceTerm() != null) {
            // 원문 용어 변경 시 중복 체크 (언어 쌍이 같을 때만)
            if (!term.getSourceTerm().equals(request.getSourceTerm()) &&
                termDictionaryRepository.existsBySourceTermAndSourceLangAndTargetLang(
                        request.getSourceTerm(), term.getSourceLang(), term.getTargetLang())) {
                throw new IllegalArgumentException("이미 존재하는 용어입니다: " + request.getSourceTerm());
            }
            term.setSourceTerm(request.getSourceTerm());
        }

        if (request.getTargetTerm() != null) {
            term.setTargetTerm(request.getTargetTerm());
        }

        if (request.getDescription() != null) {
            term.setDescription(request.getDescription());
        }

        if (request.getCategory() != null) {
            term.setCategory(request.getCategory());
        }

        if (request.getArticleTitle() != null) {
            term.setArticleTitle(request.getArticleTitle());
        }

        if (request.getArticleSource() != null) {
            term.setArticleSource(request.getArticleSource());
        }

        if (request.getArticleLink() != null) {
            term.setArticleLink(request.getArticleLink());
        }

        if (request.getMemo() != null) {
            term.setMemo(request.getMemo());
        }

        TermDictionary saved = termDictionaryRepository.save(term);
        log.info("용어 사전 수정: {} (id: {})", saved.getSourceTerm(), id);
        
        // DeepL Glossary 동기화
        try {
            syncGlossaryToDeepL(saved.getSourceLang(), saved.getTargetLang());
        } catch (Exception e) {
            log.warn("DeepL Glossary 동기화 실패 (용어는 수정됨): {}", e.getMessage());
            // 용어는 수정되었으므로 계속 진행
        }
        
        return toResponse(saved);
    }

    @Transactional
    public void deleteTerm(Long id) {
        TermDictionary term = termDictionaryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("용어를 찾을 수 없습니다: " + id));

        String sourceLang = term.getSourceLang();
        String targetLang = term.getTargetLang();
        String glossaryId = term.getDeeplGlossaryId();

        termDictionaryRepository.delete(term);
        log.info("용어 사전 삭제: {} -> {} (id: {})", term.getSourceTerm(), term.getTargetTerm(), id);
        
        // DeepL Glossary 동기화
        try {
            List<TermDictionary> remainingTerms = termDictionaryRepository
                    .findBySourceLangAndTargetLang(sourceLang, targetLang);
            
            if (remainingTerms.isEmpty()) {
                // 남은 용어가 없으면 Glossary 삭제
                if (glossaryId != null && !glossaryId.isEmpty()) {
                    try {
                        deepLGlossaryService.deleteGlossary(glossaryId);
                        log.info("DeepL Glossary 삭제 완료: glossaryId={}", glossaryId);
                    } catch (Exception e) {
                        log.warn("DeepL Glossary 삭제 실패: {}", e.getMessage());
                    }
                }
            } else {
                // 남은 용어로 Glossary 업데이트
                syncGlossaryToDeepL(sourceLang, targetLang);
            }
        } catch (Exception e) {
            log.warn("DeepL Glossary 동기화 실패 (용어는 삭제됨): {}", e.getMessage());
            // 용어는 삭제되었으므로 계속 진행
        }
    }

    private TermDictionaryResponse toResponse(TermDictionary term) {
        TermDictionaryResponse.TermDictionaryResponseBuilder builder = TermDictionaryResponse.builder()
                .id(term.getId())
                .sourceTerm(term.getSourceTerm())
                .targetTerm(term.getTargetTerm())
                .sourceLang(term.getSourceLang())
                .targetLang(term.getTargetLang())
                .description(term.getDescription())
                .category(term.getCategory())
                .articleTitle(term.getArticleTitle())
                .articleSource(term.getArticleSource())
                .articleLink(term.getArticleLink())
                .memo(term.getMemo())
                .deeplGlossaryId(term.getDeeplGlossaryId())
                .createdAt(term.getCreatedAt())
                .updatedAt(term.getUpdatedAt());

        if (term.getCreatedBy() != null) {
            builder.createdBy(TermDictionaryResponse.CreatorInfo.builder()
                    .id(term.getCreatedBy().getId())
                    .email(term.getCreatedBy().getEmail())
                    .name(term.getCreatedBy().getName())
                    .build());
        }

        return builder.build();
    }

    /**
     * 같은 언어 쌍의 모든 용어를 DeepL Glossary로 동기화
     */
    private void syncGlossaryToDeepL(String sourceLang, String targetLang) {
        // 같은 언어 쌍의 모든 용어 조회
        List<TermDictionary> terms = termDictionaryRepository
                .findBySourceLangAndTargetLang(sourceLang, targetLang);
        
        if (terms.isEmpty()) {
            log.warn("동기화할 용어가 없습니다: {} -> {}", sourceLang, targetLang);
            return;
        }
        
        // DeepL Glossary 이름 생성
        String glossaryName = String.format("Glossary_%s_%s", sourceLang, targetLang);
        
        // TermEntry 리스트 생성
        List<DeepLGlossaryService.TermEntry> entries = terms.stream()
                .map(t -> new DeepLGlossaryService.TermEntry(t.getSourceTerm(), t.getTargetTerm()))
                .collect(Collectors.toList());
        
        // 기존 Glossary ID 확인
        String existingGlossaryId = terms.stream()
                .map(TermDictionary::getDeeplGlossaryId)
                .filter(id -> id != null && !id.isEmpty())
                .findFirst()
                .orElse(null);
        
        try {
            String glossaryId;
            if (existingGlossaryId != null) {
                // 기존 Glossary가 있으면 업데이트
                try {
                    deepLGlossaryService.updateGlossaryDictionary(
                            existingGlossaryId, sourceLang, targetLang, entries);
                    glossaryId = existingGlossaryId;
                    log.info("DeepL Glossary 업데이트 완료: glossaryId={}, entries={}", 
                            glossaryId, entries.size());
                } catch (Exception e) {
                    // 업데이트 실패 시 새로 생성
                    log.warn("Glossary 업데이트 실패, 새로 생성: {}", e.getMessage());
                    glossaryId = deepLGlossaryService.createGlossary(
                            glossaryName, sourceLang, targetLang, entries);
                    log.info("DeepL Glossary 생성 완료: glossaryId={}, entries={}", 
                            glossaryId, entries.size());
                }
            } else {
                // 기존 Glossary가 없으면 새로 생성
                glossaryId = deepLGlossaryService.createGlossary(
                        glossaryName, sourceLang, targetLang, entries);
                log.info("DeepL Glossary 생성 완료: glossaryId={}, entries={}", 
                        glossaryId, entries.size());
            }
            
            // 모든 용어에 glossaryId 업데이트
            final String finalGlossaryId = glossaryId;
            terms.forEach(term -> {
                term.setDeeplGlossaryId(finalGlossaryId);
                termDictionaryRepository.save(term);
            });
            
        } catch (Exception e) {
            log.error("DeepL Glossary 동기화 실패: {}", e.getMessage(), e);
            throw e;
        }
    }
}

