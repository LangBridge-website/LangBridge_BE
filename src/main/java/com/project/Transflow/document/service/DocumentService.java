package com.project.Transflow.document.service;

import com.project.Transflow.document.dto.CreateDocumentRequest;
import com.project.Transflow.document.dto.DocumentResponse;
import com.project.Transflow.document.dto.UpdateDocumentRequest;
import com.project.Transflow.document.entity.Document;
import com.project.Transflow.document.repository.DocumentRepository;
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
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional
    public DocumentResponse createDocument(CreateDocumentRequest request, Long createdById) {
        User createdBy = userRepository.findById(createdById)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + createdById));

        Document document = Document.builder()
                .title(request.getTitle())
                .originalUrl(request.getOriginalUrl())
                .sourceLang(request.getSourceLang())
                .targetLang(request.getTargetLang())
                .categoryId(request.getCategoryId())
                .status("DRAFT")
                .estimatedLength(request.getEstimatedLength())
                .createdBy(createdBy)
                .build();

        Document saved = documentRepository.save(document);
        log.info("문서 생성: {} (id: {})", saved.getTitle(), saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentResponse> findById(Long id) {
        return documentRepository.findById(id)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findAll() {
        return documentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findByStatus(String status) {
        return documentRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findByCategoryId(Long categoryId) {
        return documentRepository.findByCategoryId(categoryId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findByCreatedBy(Long createdById) {
        return documentRepository.findByCreatedBy_Id(createdById).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public DocumentResponse updateDocument(Long id, UpdateDocumentRequest request, Long modifiedById) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + id));

        User lastModifiedBy = userRepository.findById(modifiedById)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + modifiedById));

        if (request.getTitle() != null) {
            document.setTitle(request.getTitle());
        }
        if (request.getOriginalUrl() != null) {
            document.setOriginalUrl(request.getOriginalUrl());
        }
        if (request.getSourceLang() != null) {
            document.setSourceLang(request.getSourceLang());
        }
        if (request.getTargetLang() != null) {
            document.setTargetLang(request.getTargetLang());
        }
        if (request.getCategoryId() != null) {
            document.setCategoryId(request.getCategoryId());
        }
        if (request.getStatus() != null) {
            document.setStatus(request.getStatus());
        }
        if (request.getEstimatedLength() != null) {
            document.setEstimatedLength(request.getEstimatedLength());
        }

        document.setLastModifiedBy(lastModifiedBy);

        Document saved = documentRepository.save(document);
        log.info("문서 수정: {} (id: {})", saved.getTitle(), saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void deleteDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + id));

        documentRepository.delete(document);
        log.info("문서 삭제: {} (id: {})", document.getTitle(), id);
    }

    private DocumentResponse toResponse(Document document) {
        DocumentResponse.DocumentResponseBuilder builder = DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .originalUrl(document.getOriginalUrl())
                .sourceLang(document.getSourceLang())
                .targetLang(document.getTargetLang())
                .categoryId(document.getCategoryId())
                .status(document.getStatus())
                .currentVersionId(document.getCurrentVersionId())
                .estimatedLength(document.getEstimatedLength())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt());

        if (document.getCreatedBy() != null) {
            builder.createdBy(DocumentResponse.CreatorInfo.builder()
                    .id(document.getCreatedBy().getId())
                    .email(document.getCreatedBy().getEmail())
                    .name(document.getCreatedBy().getName())
                    .build());
        }

        if (document.getLastModifiedBy() != null) {
            builder.lastModifiedBy(DocumentResponse.ModifierInfo.builder()
                    .id(document.getLastModifiedBy().getId())
                    .email(document.getLastModifiedBy().getEmail())
                    .name(document.getLastModifiedBy().getName())
                    .build());
        }

        return builder.build();
    }
}

