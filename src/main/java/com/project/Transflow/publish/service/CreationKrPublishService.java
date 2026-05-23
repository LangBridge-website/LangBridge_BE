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

        log.info("creation.kr 게시 시작 - sitePath: {}, boardId: {}, title: {}",
                request.getSitePath(), boardId, request.getTitle());

        return browserClient.publishPost(
                credentials,
                request.getSitePath(),
                boardId,
                request.getTitle(),
                request.getHtmlContent()
        );
    }

    /**
     * 승인된 리뷰 기준 creation.kr 게시
     */
    public PublishResult publishFromReview(Review review) {
        requireCredentials();

        Document document = review.getDocument();
        DocumentVersion version = review.getDocumentVersion();

        SitePathBoard mapping = categoryResolver.resolve(document.getCategoryId())
                .orElse(null);
        if (mapping == null || !mapping.hasBoardId()) {
            return PublishResult.failure(
                    "creation.kr 게시판 매핑을 찾을 수 없습니다. 카테고리에 creationKrSitePath/boardId를 설정하거나 "
                            + "application.yml board-mappings을 확인해주세요."
            );
        }

        String htmlContent = version.getContent();
        if (htmlContent == null || htmlContent.isBlank()) {
            return PublishResult.failure("게시할 HTML 본문이 없습니다.");
        }

        PublishRequest request = PublishRequest.builder()
                .title(document.getTitle())
                .htmlContent(htmlContent)
                .sitePath(mapping.getSitePath())
                .boardId(mapping.getBoardId())
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
