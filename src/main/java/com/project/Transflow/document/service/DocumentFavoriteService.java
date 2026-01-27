package com.project.Transflow.document.service;

import com.project.Transflow.document.dto.DocumentResponse;
import com.project.Transflow.document.entity.Document;
import com.project.Transflow.document.entity.DocumentFavorite;
import com.project.Transflow.document.repository.DocumentFavoriteRepository;
import com.project.Transflow.document.repository.DocumentRepository;
import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentFavoriteService {

    private final DocumentFavoriteRepository favoriteRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentService documentService;

    @Transactional
    public void addFavorite(Long userId, Long documentId) {
        // 이미 찜한 문서인지 확인
        if (favoriteRepository.existsByUserIdAndDocumentId(userId, documentId)) {
            log.warn("이미 찜한 문서입니다. userId: {}, documentId: {}", userId, documentId);
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
        
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + documentId));

        DocumentFavorite favorite = DocumentFavorite.builder()
                .user(user)
                .document(document)
                .build();

        favoriteRepository.save(favorite);
        log.info("문서 찜 추가 완료. userId: {}, documentId: {}", userId, documentId);
    }

    @Transactional
    public void removeFavorite(Long userId, Long documentId) {
        favoriteRepository.deleteByUserIdAndDocumentId(userId, documentId);
        log.info("문서 찜 제거 완료. userId: {}, documentId: {}", userId, documentId);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getFavoriteDocuments(Long userId) {
        List<DocumentFavorite> favorites = favoriteRepository.findByUserId(userId);
        return favorites.stream()
                .map(favorite -> documentService.findById(favorite.getDocument().getId())
                        .orElse(null))
                .filter(doc -> doc != null)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, Long documentId) {
        return favoriteRepository.existsByUserIdAndDocumentId(userId, documentId);
    }
}


