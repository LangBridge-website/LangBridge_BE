package com.project.Transflow.admin.service;

import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;

    /**
     * 사용자 역할 레벨 변경 (사용자 ID로)
     */
    @Transactional
    public User updateUserRoleLevel(Long userId, Integer newRoleLevel) {
        // roleLevel 검증
        if (newRoleLevel == null || newRoleLevel < 1 || newRoleLevel > 3) {
            throw new IllegalArgumentException("roleLevel은 1, 2, 3 중 하나여야 합니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        user.setRoleLevel(newRoleLevel);
        User saved = userRepository.save(user);
        log.info("사용자 역할 레벨 변경: {} (userId: {}, newRoleLevel: {})", user.getEmail(), userId, newRoleLevel);
        return saved;
    }

    /**
     * 사용자 역할 레벨 변경 (이메일로)
     */
    @Transactional
    public User updateUserRoleLevelByEmail(String email, Integer newRoleLevel) {
        // roleLevel 검증
        if (newRoleLevel == null || newRoleLevel < 1 || newRoleLevel > 3) {
            throw new IllegalArgumentException("roleLevel은 1, 2, 3 중 하나여야 합니다.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + email));

        user.setRoleLevel(newRoleLevel);
        User saved = userRepository.save(user);
        log.info("사용자 역할 레벨 변경: {} (email: {}, newRoleLevel: {})", user.getEmail(), email, newRoleLevel);
        return saved;
    }
}

