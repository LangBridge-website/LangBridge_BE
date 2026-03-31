package com.project.Transflow.inquiry.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class CreateInquiryRequest {

    @NotBlank
    @Size(max = 500)
    private String title;

    @NotBlank
    private String content;
}
