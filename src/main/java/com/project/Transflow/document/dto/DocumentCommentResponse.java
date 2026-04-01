package com.project.Transflow.document.dto;

import com.project.Transflow.document.entity.DocumentComment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentCommentResponse {

    private Long id;
    private Long documentId;
    private Long authorId;
    private String authorName;
    private String authorProfileImage;
    private String content;
    private LocalDateTime createdAt;

    public static DocumentCommentResponse from(DocumentComment comment) {
        return DocumentCommentResponse.builder()
                .id(comment.getId())
                .documentId(comment.getDocument().getId())
                .authorId(comment.getAuthor().getId())
                .authorName(comment.getAuthor().getName())
                .authorProfileImage(comment.getAuthor().getProfileImage())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
