package com.project.Transflow.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "사용자 역할 레벨 변경 응답")
public class UpdateUserRoleResponse {

    @Schema(description = "성공 여부", example = "true")
    private Boolean success;

    @Schema(description = "메시지", example = "사용자 레벨이 변경되었습니다.")
    private String message;

    @Schema(description = "변경된 사용자 정보")
    private UserInfo user;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "사용자 정보")
    public static class UserInfo {
        @Schema(description = "사용자 ID", example = "1")
        private Long id;

        @Schema(description = "이메일", example = "user@example.com")
        private String email;

        @Schema(description = "이름", example = "홍길동")
        private String name;

        @Schema(description = "역할 레벨", example = "2")
        private Integer roleLevel;
    }
}

