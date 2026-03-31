package com.project.Transflow.inquiry.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class CreateReplyRequest {

    @NotBlank
    private String content;
}
