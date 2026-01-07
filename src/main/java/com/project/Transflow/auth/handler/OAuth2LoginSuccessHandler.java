package com.project.Transflow.auth.handler;

import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import com.project.Transflow.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oauth2User.getAttributes();
        
        String email = (String) attributes.get("email");
        log.info("OAuth2 로그인 성공: {}", email);
        
        if (email == null) {
            log.error("이메일 정보가 없습니다.");
            getRedirectStrategy().sendRedirect(request, response, "/api/auth/login/failure?error=no_email");
            return;
        }
        
        Optional<User> userOpt = userRepository.findByEmail(email);
        
        if (userOpt.isEmpty()) {
            log.error("사용자를 찾을 수 없습니다: {}", email);
            getRedirectStrategy().sendRedirect(request, response, "/api/auth/login/failure?error=user_not_found");
            return;
        }
        
        User user = userOpt.get();
        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getRoleLevel());
        
        // JWT 토큰을 쿼리 파라미터로 전달
        String targetUrl = UriComponentsBuilder.fromUriString("/api/auth/login/success")
                .queryParam("token", token)
                .queryParam("email", email)
                .build().toUriString();
        
        log.info("로그인 성공, 토큰 생성 완료: {} (role_level: {})", email, user.getRoleLevel());
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}

