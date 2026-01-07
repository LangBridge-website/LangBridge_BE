package com.project.Transflow.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Min;
import javax.validation.constraints.Max;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 역할 레벨 변경 요청")
public class UpdateUserRoleRequest {

    @Schema(description = "역할 레벨 (1: 최고관리자, 2: 관리자, 3: 번역봉사자)", example = "2", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"1", "2", "3"})
    @NotNull(message = "roleLevel은 필수입니다.")
    @Min(value = 1, message = "roleLevel은 1, 2, 3 중 하나여야 합니다.")
    @Max(value = 3, message = "roleLevel은 1, 2, 3 중 하나여야 합니다.")
    private Integer roleLevel;
}

