package com.project.Transflow.document.service;

import com.project.Transflow.document.dto.HandoverRequest;
import com.project.Transflow.document.entity.Document;
import com.project.Transflow.document.entity.HandoverHistory;
import com.project.Transflow.document.repository.DocumentRepository;
import com.project.Transflow.document.repository.HandoverHistoryRepository;
import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HandoverHistoryService {

    private final HandoverHistoryRepository handoverHistoryRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional
    public HandoverHistory createHandover(Long documentId, HandoverRequest request, Long userId) {
        log.info("인계 히스토리 생성: documentId={}, userId={}", documentId, userId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));

        // 사용자 조회
        User user;
        if (userId != null) {
            user = userRepository.findById(userId)
                    .orElseGet(() -> {
                        log.warn("사용자 ID {}를 찾을 수 없어 기본 사용자를 사용합니다.", userId);
                        return userRepository.findAll().stream()
                                .filter(u -> u.getRoleLevel() <= 2)
                                .findFirst()
                                .orElseGet(() -> userRepository.findAll().stream()
                                        .findFirst()
                                        .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                "시스템에 사용자가 없습니다.")));
                    });
        } else {
            user = userRepository.findAll().stream()
                    .filter(u -> u.getRoleLevel() <= 2)
                    .findFirst()
                    .orElseGet(() -> userRepository.findAll().stream()
                            .findFirst()
                            .orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    "시스템에 사용자가 없습니다.")));
            log.warn("userId가 null이어서 기본 사용자 사용: {}", user.getId());
        }

        // completedParagraphs를 JSON 문자열로 변환
        String completedParagraphsJson = null;
        if (request.getCompletedParagraphs() != null && !request.getCompletedParagraphs().isEmpty()) {
            completedParagraphsJson = request.getCompletedParagraphs().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",", "[", "]"));
        }

        HandoverHistory handoverHistory = HandoverHistory.builder()
                .document(document)
                .handedOverBy(user)
                .memo(request.getMemo())
                .terms(request.getTerms())
                .completedParagraphs(completedParagraphsJson)
                .build();

        HandoverHistory saved = handoverHistoryRepository.save(handoverHistory);
        log.info("인계 히스토리 생성 완료: handoverHistoryId={}, documentId={}, userId={}", 
                saved.getId(), documentId, userId);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<HandoverHistory> findAllByDocumentId(Long documentId) {
        return handoverHistoryRepository.findByDocument_IdOrderByCreatedAtDesc(documentId);
    }

    @Transactional(readOnly = true)
    public Optional<HandoverHistory> findLatestByDocumentId(Long documentId) {
        return handoverHistoryRepository.findLatestByDocumentId(documentId);
    }

    @Transactional(readOnly = true)
    public List<HandoverHistory> findAllByUserId(Long userId) {
        return handoverHistoryRepository.findByHandedOverBy_IdOrderByCreatedAtDesc(userId);
    }
}

