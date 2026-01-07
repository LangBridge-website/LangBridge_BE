package com.project.Transflow.user.service;

import com.project.Transflow.admin.entity.AdminWhitelist;
import com.project.Transflow.admin.repository.AdminWhitelistRepository;
import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final AdminWhitelistRepository adminWhitelistRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        if (!"google".equals(registrationId)) {
            throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
        }

        Map<String, Object> attributes = oauth2User.getAttributes();
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String googleId = (String) attributes.get("sub");
        String profileImage = (String) attributes.get("picture");

        // 사용자 생성 또는 업데이트
        User user = createOrUpdateUser(email, name, googleId, profileImage);

        // 권한 설정
        String role = "ROLE_USER";
        if (user.getRoleLevel() == 1) {
            role = "ROLE_SUPER_ADMIN";
        } else if (user.getRoleLevel() == 2) {
            role = "ROLE_ADMIN";
        }

        return new DefaultOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority(role)),
            attributes,
            "sub"
        );
    }

    private User createOrUpdateUser(String email, String name, String googleId, String profileImage) {
        User user = userRepository.findByEmail(email)
            .orElse(null);

        if (user == null) {
            // 신규 사용자 생성
            int roleLevel = determineInitialRoleLevel(email);
            
            user = User.builder()
                .email(email)
                .name(name)
                .googleId(googleId)
                .profileImage(profileImage)
                .roleLevel(roleLevel)
                .build();
            
            log.info("신규 사용자 생성: {} (role_level: {})", email, roleLevel);
        } else {
            // 기존 사용자 정보 업데이트
            user.setGoogleId(googleId);
            user.setName(name);
            if (profileImage != null) {
                user.setProfileImage(profileImage);
            }
            log.info("기존 사용자 정보 업데이트: {}", email);
        }

        return userRepository.save(user);
    }

    private int determineInitialRoleLevel(String email) {
        // 1. 화이트리스트 체크 (최고관리자)
        AdminWhitelist superAdmin = adminWhitelistRepository.findByEmail(email)
            .filter(whitelist -> whitelist.getRoleLevel() == 1)
            .orElse(null);
        
        if (superAdmin != null) {
            return 1;
        }

        // 2. 화이트리스트 체크 (관리자)
        AdminWhitelist admin = adminWhitelistRepository.findByEmail(email)
            .filter(whitelist -> whitelist.getRoleLevel() == 2)
            .orElse(null);
        
        if (admin != null) {
            return 2;
        }

        // 3. 첫 번째 사용자면 최고관리자 (안전장치)
        if (userRepository.count() == 0) {
            log.warn("첫 번째 사용자가 최고관리자로 자동 할당됨: {}", email);
            return 1;
        }

        // 4. 기본값: 번역봉사자
        return 3;
    }
}

