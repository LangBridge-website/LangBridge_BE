package com.project.Transflow.notification.service;

import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationNotificationMailService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    @Value("${app.reviews.url:https://lb.walab.info/reviews}")
    private String reviewsUrl;

    public void sendTranslationCompletedToAdmins(Long documentId, String documentTitle) {
        List<String> recipients = userRepository.findByRoleLevelLessThanEqual(2).stream()
                .map(User::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .distinct()
                .collect(Collectors.toList());

        if (recipients.isEmpty()) {
            log.info("번역 완료 알림 메일 스킵 - 관리자 수신자 없음: documentId={}", documentId);
            return;
        }

        String safeTitle = (documentTitle == null || documentTitle.isBlank()) ? "(제목 없음)" : documentTitle;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipients.toArray(new String[0]));
        message.setSubject("[LangBridge] 번역 완료 알림");
        message.setText(
                "번역이 완료되었습니다.\n\n"
                        + "- 문서 ID: " + documentId + "\n"
                        + "- 문서명: " + safeTitle + "\n\n"
                        + "아래 리뷰 페이지에서 확인해 주세요.\n"
                        + reviewsUrl
        );

        mailSender.send(message);
        log.info("번역 완료 알림 메일 발송 완료: documentId={}, recipients={}", documentId, recipients.size());
    }
}
