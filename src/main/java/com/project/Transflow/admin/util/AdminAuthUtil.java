package com.project.Transflow.admin.util;

import com.project.Transflow.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 관리자 권한 체크 유틸리티
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthUtil {

    private final JwtUtil jwtUtil;

    /**
     * JWT 토큰에서 roleLevel 추출
     */
    public Integer getRoleLevelFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            String token = authHeader.substring(7);
            if (!jwtUtil.validateToken(token)) {
                return null;
            }
            return jwtUtil.extractRoleLevel(token);
        } catch (Exception e) {
            log.error("토큰에서 roleLevel 추출 실패", e);
            return null;
        }
    }

    /**
     * 최고관리자 권한 체크 (roleLevel == 1)
     */
    public boolean isSuperAdmin(String authHeader) {
        Integer roleLevel = getRoleLevelFromToken(authHeader);
        return roleLevel != null && roleLevel == 1;
    }

    /**
     * 관리자 이상 권한 체크 (roleLevel == 1 or 2)
     */
    public boolean isAdminOrAbove(String authHeader) {
        Integer roleLevel = getRoleLevelFromToken(authHeader);
        return roleLevel != null && (roleLevel == 1 || roleLevel == 2);
    }

    /**
     * JWT 토큰에서 userId 추출
     */
    public Long getUserIdFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            String token = authHeader.substring(7);
            if (!jwtUtil.validateToken(token)) {
                return null;
            }
            return jwtUtil.extractUserId(token);
        } catch (Exception e) {
            log.error("토큰에서 userId 추출 실패", e);
            return null;
        }
    }
}

