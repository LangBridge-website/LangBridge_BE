package com.project.Transflow.document.service;

import com.project.Transflow.document.dto.CreateDocumentCommentRequest;
import com.project.Transflow.document.dto.DocumentCommentResponse;
import com.project.Transflow.document.entity.Document;
import com.project.Transflow.document.entity.DocumentComment;
import com.project.Transflow.document.repository.DocumentCommentRepository;
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
public class DocumentCommentService {

    private final DocumentCommentRepository commentRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    /**
     * 문서에 달린 댓글 목록을 시간 오름차순으로 반환합니다.
     * 복사본 문서인 경우 원문 문서의 채팅을 공유합니다.
     */
    @Transactional(readOnly = true)
    public List<DocumentCommentResponse> getComments(Long documentId) {
        Long rootId = resolveRootDocumentId(documentId);
        return commentRepository.findByDocument_IdOrderByCreatedAtAsc(rootId)
                .stream()
                .map(DocumentCommentResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 댓글을 작성합니다.
     * 복사본 문서인 경우 원문 문서의 채팅방에 작성됩니다.
     */
    @Transactional
    public DocumentCommentResponse addComment(Long documentId, Long authorId, CreateDocumentCommentRequest request) {
        Long rootId = resolveRootDocumentId(documentId);

        Document rootDocument = documentRepository.findById(rootId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + rootId));

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + authorId));

        DocumentComment comment = DocumentComment.builder()
                .document(rootDocument)
                .author(author)
                .content(request.getContent().trim())
                .build();

        DocumentComment saved = commentRepository.save(comment);
        log.info("댓글 저장 완료: rootDocumentId={}, requestedDocumentId={}, authorId={}",
                rootId, documentId, authorId);
        return DocumentCommentResponse.from(saved);
    }

    /**
     * 댓글을 삭제합니다. 작성자 본인 또는 관리자(roleLevel 1,2)만 삭제 가능합니다.
     */
    @Transactional
    public void deleteComment(Long commentId, Long requestUserId, Integer requestRoleLevel) {
        DocumentComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다: " + commentId));

        boolean isAuthor = comment.getAuthor().getId().equals(requestUserId);
        boolean isAdmin = requestRoleLevel != null && requestRoleLevel <= 2;

        if (!isAuthor && !isAdmin) {
            throw new SecurityException("댓글을 삭제할 권한이 없습니다.");
        }

        commentRepository.delete(comment);
        log.info("댓글 삭제 완료: commentId={}, deletedBy={}", commentId, requestUserId);
    }

    /**
     * 복사본 문서인 경우 원문(root) 문서 ID를 반환합니다.
     * 원문이거나 sourceDocument가 없으면 전달받은 documentId를 그대로 반환합니다.
     *
     * <p>예: 원문(id=1) → 복사본(id=5, sourceDocumentId=1)
     * resolveRootDocumentId(5) → 1
     * resolveRootDocumentId(1) → 1
     */
    private Long resolveRootDocumentId(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + documentId));

        if (document.getSourceDocument() != null) {
            Long rootId = document.getSourceDocument().getId();
            log.debug("채팅 root 문서 귀결: documentId={} → rootId={}", documentId, rootId);
            return rootId;
        }
        return documentId;
    }
}
