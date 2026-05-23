package com.project.Transflow.publish.service;

import com.project.Transflow.document.entity.Document;
import com.project.Transflow.document.entity.DocumentVersion;
import com.project.Transflow.publish.config.CreationKrProperties;
import com.project.Transflow.review.entity.Review;
import com.project.Transflow.publish.dto.PublishRequest;
import com.project.Transflow.publish.dto.PublishResult;
import com.project.Transflow.publish.service.CreationKrCategoryResolver.SitePathBoard;
import com.project.Transflow.settings.dto.CreationKrCredentials;
import com.project.Transflow.settings.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreationKrPublishService {

    private final ApiKeyService apiKeyService;
    private final CreationKrBrowserClient browserClient;
    private final CreationKrProperties properties;
    private final CreationKrCategoryResolver categoryResolver;
    private final CreationKrPublishHtmlSanitizer htmlSanitizer;

    /**
     * 등록된 계정으로 creation.kr 로그인 테스트
     */
    public PublishResult testConnection() {
        CreationKrCredentials credentials = requireCredentials();
        return browserClient.testLogin(credentials);
    }

    /**
     * creation.kr 게시판에 글 등록 (POC)
     */
    public PublishResult publish(PublishRequest request) {
        CreationKrCredentials credentials = requireCredentials();

        String boardId = request.getBoardId();
        if (boardId == null || boardId.isBlank()) {
            boardId = properties.resolveBoardId(request.getSitePath());
        }
        if (boardId == null || boardId.isBlank()) {
            return PublishResult.failure(
                    "boardId를 찾을 수 없습니다. request.boardId 또는 creation-kr.board-mappings에 "
                            + request.getSitePath() + " 매핑을 추가해주세요."
            );
        }

        String sanitizedHtml = htmlSanitizer.sanitize(
                request.getHtmlContent(),
                request.getOriginalUrl()
        );
        if (sanitizedHtml == null || sanitizedHtml.isBlank()) {
            return PublishResult.failure("게시할 본문 HTML이 비어 있습니다.");
        }

        log.info("creation.kr 게시 시작 - sitePath: {}, boardId: {}, title: {}, htmlLength: {}",
                request.getSitePath(), boardId, request.getTitle(), sanitizedHtml.length());

        return browserClient.publishPost(
                credentials,
                request.getSitePath(),
                boardId,
                request.getTitle(),
                sanitizedHtml
        );
    }

    /**
     * 승인된 리뷰 기준 creation.kr 게시
     */
    public PublishResult publishFromReview(Review review) {
        return publishFromReview(review, null, null);
    }

    /**
     * 승인된 리뷰 기준 creation.kr 게시 (게시판 수동 선택)
     */
    public PublishResult publishFromReview(Review review, String sitePathOverride, String boardIdOverride) {
        requireCredentials();

        Document document = review.getDocument();
        DocumentVersion version = review.getDocumentVersion();

        String sitePath = sitePathOverride;
        String boardId = boardIdOverride;

        if (sitePath == null || sitePath.isBlank() || boardId == null || boardId.isBlank()) {
            SitePathBoard mapping = categoryResolver.resolve(document.getCategoryId())
                    .orElse(null);
            if (mapping == null || !mapping.hasBoardId()) {
                return PublishResult.failure(
                        "creation.kr 게시판을 선택해주세요. 문서 카테고리에 매핑이 없습니다."
                );
            }
            if (sitePath == null || sitePath.isBlank()) {
                sitePath = mapping.getSitePath();
            }
            if (boardId == null || boardId.isBlank()) {
                boardId = mapping.getBoardId();
            }
        }

        if (sitePath == null || sitePath.isBlank() || boardId == null || boardId.isBlank()) {
            return PublishResult.failure("creation.kr 게시판(sitePath, boardId)이 필요합니다.");
        }

        String htmlContent = version.getContent();
        if (htmlContent == null || htmlContent.isBlank()) {
            return PublishResult.failure("게시할 HTML 본문이 없습니다.");
        }

        PublishRequest request = PublishRequest.builder()
                .title(document.getTitle())
                .htmlContent(htmlContent)
                .sitePath(sitePath.trim())
                .boardId(boardId.trim())
                .originalUrl(document.getOriginalUrl())
                .build();

        return publish(request);
    }

    private CreationKrCredentials requireCredentials() {
        CreationKrCredentials credentials = apiKeyService.getDecryptedCreationKrCredentials();
        if (credentials == null) {
            throw new IllegalStateException(
                    "creation.kr 계정이 등록되지 않았습니다. 시스템 설정에서 계정을 등록해주세요."
            );
        }
        return credentials;
    }
}
