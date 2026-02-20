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
import org.springframework.data.domain.PageRequest;
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

    @Transactional(timeout = 30)
    public DocumentLock acquireLock(Long documentId, Long userId) {
        log.info("🔒 락 획득 시도: documentId={}, userId={}", documentId, userId);
        
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));

        // 개발 단계: userId가 null이거나 사용자를 찾지 못하면 기본 사용자 사용
        User user;
        if (userId != null) {
            user = userRepository.findById(userId)
                    .orElseGet(() -> {
                        // 사용자를 찾지 못하면 기본 사용자 찾기
                        log.warn("사용자 ID {}를 찾을 수 없어 기본 사용자를 사용합니다.", userId);
                        return userRepository.findAll().stream()
                                .filter(u -> u.getRoleLevel() <= 2) // 관리자 이상
                                .findFirst()
                                .orElseGet(() -> userRepository.findAll().stream()
                                        .findFirst()
                                        .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                "시스템에 사용자가 없습니다. 먼저 사용자를 생성해주세요.")));
                    });
        } else {
            // userId가 null이면 기본 사용자 찾기
            user = userRepository.findAll().stream()
                    .filter(u -> u.getRoleLevel() <= 2) // 관리자 이상
                    .findFirst()
                    .orElseGet(() -> userRepository.findAll().stream()
                            .findFirst()
                            .orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    "시스템에 사용자가 없습니다. 먼저 사용자를 생성해주세요.")));
            log.warn("userId가 null이어서 기본 사용자 사용: {}", user.getId());
        }

        // PENDING_TRANSLATION 문서는 인계 후 정리되지 않은 스테일 락이 있을 수 있으므로 선제 삭제
        if ("PENDING_TRANSLATION".equals(document.getStatus())) {
            int deleted = lockRepository.deleteAllByDocumentId(documentId);
            if (deleted > 0) {
                log.warn("⚠️ PENDING_TRANSLATION 문서의 스테일 락 {}건 선제 삭제: documentId={}", deleted, documentId);
            }
        }

        // 이미 락이 있는지 확인
        Optional<DocumentLock> existingLock = lockRepository.findFirstByDocument_Id(documentId);
        if (existingLock.isPresent()) {
            DocumentLock lock = existingLock.get();
            // userId가 null이면 비교하지 않고 기존 락 반환 (개발 단계)
            if (userId == null || lock.getLockedBy().getId().equals(user.getId())) {
                log.info("✅ 이미 같은 사용자가 락을 보유하고 있습니다: documentId={}, userId={}", documentId, userId);
                return lock;
            }
            // IN_TRANSLATION 등 다른 상태에서 다른 사용자가 작업 중
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이 문서는 다른 사용자가 작업 중입니다: " + lock.getLockedBy().getName()
            );
        }

        // 새 락 생성 시도
        try {
            DocumentLock lock = DocumentLock.builder()
                    .document(document)
                    .lockedBy(user)
                    .build();

            DocumentLock saved = lockRepository.saveAndFlush(lock);
            log.info("✅ 문서 락 DB 저장 완료: documentId={}, userId={}, lockId={}",
                    documentId, userId, saved.getId());

            // 문서 상태를 IN_TRANSLATION으로 변경
            document.setStatus("IN_TRANSLATION");
            documentRepository.saveAndFlush(document);
            log.info("✅ 문서 상태 업데이트 완료: documentId={}, status=IN_TRANSLATION", documentId);

            return saved;

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 유니크 제약조건 위반
            // ⚠️ 중요: DataIntegrityViolationException 발생 후 Hibernate 세션이 오염됨
            //    이 catch 블록에서 DB 쿼리를 실행하면 AssertionFailure(null id) 가 발생하므로
            //    절대 DB 조회를 하지 말고 즉시 예외를 던져야 함
            log.warn("⚠️ 유니크 제약조건 위반 (다른 요청이 먼저 락을 획득): documentId={}", documentId);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "문서 락을 획득할 수 없습니다. 잠시 후 다시 시도해주세요."
            );
        } catch (org.hibernate.exception.LockAcquisitionException |
                 org.springframework.dao.CannotAcquireLockException e) {
            log.error("❌ DB 락 획득 실패: documentId={}, userId={}", documentId, userId, e);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "문서 락을 획득하는 중 데이터베이스 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
            );
        } catch (Exception e) {
            log.error("❌ 락 저장 중 예상치 못한 오류: documentId={}, userId={}", documentId, userId, e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "문서 락을 저장하는 중 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }

    @Transactional
    public void releaseLock(Long documentId, Long userId) {
        Optional<DocumentLock> lockOpt = lockRepository.findFirstByDocument_Id(documentId);
        if (lockOpt.isEmpty()) {
            log.warn("락이 존재하지 않습니다: documentId={}", documentId);
            return;
        }

        DocumentLock lock = lockOpt.get();
        
        // userId가 null이면 락을 보유한 사용자와 비교하지 않고 해제 (개발 단계)
        if (userId != null && !lock.getLockedBy().getId().equals(userId)) {
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
        try {
            log.debug("🔍 락 상태 조회 시작: documentId={}", documentId);
            
            // LAZY 로딩 문제 해결을 위해 JOIN FETCH 사용
            Optional<DocumentLock> lockOpt = lockRepository.findByDocumentIdWithUserList(documentId, PageRequest.of(0, 1)).stream().findFirst();
            
            if (lockOpt.isPresent()) {
                DocumentLock lock = lockOpt.get();
                log.debug("✅ 락 발견: lockId={}, documentId={}", lock.getId(), documentId);
                
                // LAZY 로딩 강제 초기화 (트랜잭션 내에서)
                try {
                    if (lock.getLockedBy() != null) {
                        Long lockedById = lock.getLockedBy().getId();
                        String lockedByName = lock.getLockedBy().getName();
                        String lockedByEmail = lock.getLockedBy().getEmail();
                        log.debug("✅ lockedBy 정보 로드 완료: userId={}, name={}, email={}", 
                                lockedById, lockedByName, lockedByEmail);
                    } else {
                        log.warn("⚠️ lockedBy가 null입니다: lockId={}", lock.getId());
                    }
                    
                    if (lock.getDocument() != null) {
                        Long docId = lock.getDocument().getId();
                        log.debug("✅ document 정보 로드 완료: documentId={}", docId);
                    } else {
                        log.warn("⚠️ document가 null입니다: lockId={}", lock.getId());
                    }
                } catch (Exception e) {
                    log.error("❌ LAZY 로딩 중 오류 발생: documentId={}", documentId, e);
                    // LAZY 로딩 실패해도 락은 반환 (부분 정보라도 제공)
                }
            } else {
                log.debug("ℹ️ 락이 없습니다: documentId={}", documentId);
            }
            
            return lockOpt;
        } catch (Exception e) {
            log.error("❌ 락 상태 조회 중 오류 발생: documentId={}", documentId, e);
            return Optional.empty(); // 에러 발생 시 빈 Optional 반환
        }
    }

    @Transactional(readOnly = true)
    public boolean isLockedByUser(Long documentId, Long userId) {
        Optional<DocumentLock> lockOpt = lockRepository.findFirstByDocument_Id(documentId);
        if (lockOpt.isEmpty()) {
            return false;
        }
        return lockOpt.get().getLockedBy().getId().equals(userId);
    }
}

