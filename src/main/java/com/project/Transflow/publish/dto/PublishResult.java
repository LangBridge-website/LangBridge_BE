package com.project.Transflow.publish.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublishResult {

    private boolean success;
    private String publishedUrl;
    private String errorMessage;

    public static PublishResult success(String publishedUrl) {
        return PublishResult.builder()
                .success(true)
                .publishedUrl(publishedUrl)
                .build();
    }

    public static PublishResult failure(String errorMessage) {
        return PublishResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
