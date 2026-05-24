package com.project.Transflow.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 내부 사용: 복호화된 creation.kr 로그인 정보 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreationKrCredentials {

    private String email;
    private String password;
}
