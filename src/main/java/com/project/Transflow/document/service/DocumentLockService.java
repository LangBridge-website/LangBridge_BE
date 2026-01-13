package com.project.Transflow.document.service;

import com.project.Transflow.document.entity.Document;
import com.project.Transflow.document.entity.DocumentLock;
import com.project.Transflow.document.repository.DocumentLockRepository;
import com.project.Transflow.document.repository.DocumentRepository;
import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentLockService {

    private final DocumentLockRepository lockRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional
    public DocumentLock acquireLock(Long documentId, Long userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 이미 락이 있는지 확인
        Optional<DocumentLock> existingLock = lockRepository.findByDocumentId(documentId);
        if (existingLock.isPresent()) {
            DocumentLock lock = existingLock.get();
            // 같은 사용자가 락을 가지고 있으면 그대로 반환
            if (lock.getLockedBy().getId().equals(userId)) {
                log.info("이미 같은 사용자가 락을 보유하고 있습니다: documentId={}, userId={}", documentId, userId);
                return lock;
            }
            // 다른 사용자가 락을 가지고 있으면 예외 발생
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이 문서는 다른 사용자가 작업 중입니다: " + lock.getLockedBy().getName()
            );
        }

        // 새 락 생성
        DocumentLock lock = DocumentLock.builder()
                .document(document)
                .lockedBy(user)
                .build();

        DocumentLock saved = lockRepository.save(lock);
        log.info("문서 락 획득: documentId={}, userId={}", documentId, userId);

        // 문서 상태를 IN_TRANSLATION으로 변경
        document.setStatus("IN_TRANSLATION");
        documentRepository.save(document);

        return saved;
    }

    @Transactional
    public void releaseLock(Long documentId, Long userId) {
        Optional<DocumentLock> lockOpt = lockRepository.findByDocumentId(documentId);
        if (lockOpt.isEmpty()) {
            log.warn("락이 존재하지 않습니다: documentId={}", documentId);
            return;
        }

        DocumentLock lock = lockOpt.get();
        if (!lock.getLockedBy().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "락을 해제할 권한이 없습니다."
            );
        }

        lockRepository.delete(lock);
        log.info("문서 락 해제: documentId={}, userId={}", documentId, userId);
    }

    @Transactional
    public void releaseLockByAdmin(Long documentId) {
        lockRepository.deleteByDocumentId(documentId);
        log.info("관리자에 의해 문서 락 강제 해제: documentId={}", documentId);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentLock> getLockStatus(Long documentId) {
        return lockRepository.findByDocumentId(documentId);
    }

    @Transactional(readOnly = true)
    public boolean isLockedByUser(Long documentId, Long userId) {
        Optional<DocumentLock> lockOpt = lockRepository.findByDocumentId(documentId);
        if (lockOpt.isEmpty()) {
            return false;
        }
        return lockOpt.get().getLockedBy().getId().equals(userId);
    }
}

