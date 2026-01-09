package com.project.Transflow.term.service;

import com.project.Transflow.term.dto.CreateTermRequest;
import com.project.Transflow.term.dto.TermDictionaryResponse;
import com.project.Transflow.term.dto.UpdateTermRequest;
import com.project.Transflow.term.entity.TermDictionary;
import com.project.Transflow.term.repository.TermDictionaryRepository;
import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TermDictionaryService {

    private final TermDictionaryRepository termDictionaryRepository;
    private final UserRepository userRepository;

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
                .createdBy(createdBy)
                .build();

        TermDictionary saved = termDictionaryRepository.save(term);
        log.info("용어 사전 추가: {} -> {} ({} -> {})", request.getSourceTerm(), request.getTargetTerm(), 
                request.getSourceLang(), request.getTargetLang());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TermDictionaryResponse> findAll() {
        return termDictionaryRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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

        TermDictionary saved = termDictionaryRepository.save(term);
        log.info("용어 사전 수정: {} (id: {})", saved.getSourceTerm(), id);
        return toResponse(saved);
    }

    @Transactional
    public void deleteTerm(Long id) {
        TermDictionary term = termDictionaryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("용어를 찾을 수 없습니다: " + id));

        termDictionaryRepository.delete(term);
        log.info("용어 사전 삭제: {} -> {} (id: {})", term.getSourceTerm(), term.getTargetTerm(), id);
    }

    private TermDictionaryResponse toResponse(TermDictionary term) {
        TermDictionaryResponse.TermDictionaryResponseBuilder builder = TermDictionaryResponse.builder()
                .id(term.getId())
                .sourceTerm(term.getSourceTerm())
                .targetTerm(term.getTargetTerm())
                .sourceLang(term.getSourceLang())
                .targetLang(term.getTargetLang())
                .description(term.getDescription())
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
}

