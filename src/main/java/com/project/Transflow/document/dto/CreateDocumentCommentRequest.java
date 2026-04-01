package com.project.Transflow.document.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Getter
@NoArgsConstructor
public class CreateDocumentCommentRequest {

    @NotBlank(message = "내용을 입력해주세요.")
    @Size(max = 2000, message = "댓글은 2000자 이하로 입력해주세요.")
    private String content;
}
