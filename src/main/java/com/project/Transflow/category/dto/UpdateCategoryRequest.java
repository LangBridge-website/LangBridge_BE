package com.project.Transflow.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "카테고리 수정 요청")
public class UpdateCategoryRequest {

    @Schema(description = "카테고리 이름", example = "기술 문서 (수정)")
    @Size(max = 100, message = "카테고리 이름은 100자 이하여야 합니다.")
    private String name;

    @Schema(description = "카테고리 설명", example = "기술 관련 문서 카테고리 (업데이트)")
    private String description;
}

