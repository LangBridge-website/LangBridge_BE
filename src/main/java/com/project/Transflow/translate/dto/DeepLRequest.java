package com.project.Transflow.translate.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeepLRequest {
    private String text;
    private String target_lang;
    private String source_lang;
}
