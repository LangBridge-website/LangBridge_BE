package com.project.Transflow.publish.service;

import com.project.Transflow.publish.config.CreationKrProperties;
import com.project.Transflow.publish.dto.PublishRequest;
import com.project.Transflow.publish.dto.PublishResult;
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
