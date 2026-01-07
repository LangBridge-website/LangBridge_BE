package com.project.Transflow.auth.dto;

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
@Schema(description = "사용자 정보 응답")
public class UserResponse {

    @Schema(description = "사용자 ID", example = "1")
    private Long id;

    @Schema(description = "이메일", example = "user@example.com")
    private String email;

    @Schema(description = "이름", example = "홍길동")
    private String name;

    @Schema(description = "역할 레벨 (1: 최고관리자, 2: 관리자, 3: 번역봉사자)", example = "3")
    private Integer roleLevel;

    @Schema(description = "프로필 이미지 URL", example = "https://lh3.googleusercontent.com/...")
    private String profileImage;
}

