package com.project.Transflow.task.service;

import com.project.Transflow.document.entity.Document;
import com.project.Transflow.document.repository.DocumentRepository;
import com.project.Transflow.task.dto.CreateTranslationTaskRequest;
import com.project.Transflow.task.dto.TranslationTaskResponse;
import com.project.Transflow.task.entity.TranslationTask;
import com.project.Transflow.task.repository.TranslationTaskRepository;
import com.project.Transflow.user.entity.User;
import com.project.Transflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationTaskService {

    private final TranslationTaskRepository translationTaskRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional
    public TranslationTaskResponse createTask(CreateTranslationTaskRequest request, Long currentUserId, Long assignedById) {
        Document document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + request.getDocumentId()));

        // translator_id 결정
        Long translatorId = request.getTranslatorId() != null ? request.getTranslatorId() : currentUserId;
        User translator = userRepository.findById(translatorId)
                .orElseThrow(() -> new IllegalArgumentException("번역봉사자를 찾을 수 없습니다: " + translatorId));

        // 같은 번역가가 같은 문서에 대해 IN_PROGRESS 상태의 작업이 있는지 확인
        // SUBMITTED 상태는 허용 (부분 번역 후 다른 번역가가 이어서 작업할 수 있음)
        Optional<TranslationTask> existingTask = translationTaskRepository
                .findByDocument_IdAndTranslator_Id(request.getDocumentId(), translatorId);
        if (existingTask.isPresent() && "IN_PROGRESS".equals(existingTask.get().getStatus())) {
            throw new IllegalArgumentException("이미 해당 문서에 대한 진행 중인 번역 작업이 존재합니다.");
        }
        
        // 같은 번역가가 같은 문서에 대해 AVAILABLE 상태의 작업이 있으면 재사용
        if (existingTask.isPresent() && "AVAILABLE".equals(existingTask.get().getStatus())) {
            log.info("기존 AVAILABLE 작업 재사용: 작업 ID {}", existingTask.get().getId());
            return toResponse(existingTask.get());
        }

        // assigned_by 결정
        User assignedBy = null;
        if (request.getIsAssigned() != null && request.getIsAssigned() && assignedById != null) {
            assignedBy = userRepository.findById(assignedById)
                    .orElseThrow(() -> new IllegalArgumentException("할당자를 찾을 수 없습니다: " + assignedById));
        }

        TranslationTask task = TranslationTask.builder()
                .document(document)
                .translator(translator)
                .assignedBy(assignedBy)
                .status("AVAILABLE")
                .build();

        TranslationTask saved = translationTaskRepository.save(task);
        log.info("번역 작업 생성: 문서 ID {}, 번역봉사자 ID {}", request.getDocumentId(), translatorId);
        return toResponse(saved);
    }

    @Transactional
    public TranslationTaskResponse startTask(Long taskId, Long translatorId) {
        TranslationTask task = translationTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("번역 작업을 찾을 수 없습니다: " + taskId));

        // 권한 체크: 본인의 작업인지 확인
        if (!task.getTranslator().getId().equals(translatorId)) {
            throw new IllegalArgumentException("본인의 번역 작업만 시작할 수 있습니다.");
        }

        if (!"AVAILABLE".equals(task.getStatus())) {
            throw new IllegalArgumentException("작업을 시작할 수 없는 상태입니다. 현재 상태: " + task.getStatus());
        }

        task.setStatus("IN_PROGRESS");
        task.setStartedAt(LocalDateTime.now());
        task.setLastActivityAt(LocalDateTime.now());

        // Document 상태 업데이트
        task.getDocument().setStatus("IN_TRANSLATION");
        documentRepository.save(task.getDocument());

        TranslationTask saved = translationTaskRepository.save(task);
        log.info("번역 작업 시작: 작업 ID {}", taskId);
        return toResponse(saved);
    }

    @Transactional
    public TranslationTaskResponse submitTask(Long taskId, Long translatorId) {
        TranslationTask task = translationTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("번역 작업을 찾을 수 없습니다: " + taskId));

        // 권한 체크
        if (!task.getTranslator().getId().equals(translatorId)) {
            throw new IllegalArgumentException("본인의 번역 작업만 제출할 수 있습니다.");
        }

        if (!"IN_PROGRESS".equals(task.getStatus())) {
            throw new IllegalArgumentException("제출할 수 없는 상태입니다. 현재 상태: " + task.getStatus());
        }

        task.setStatus("SUBMITTED");
        task.setSubmittedAt(LocalDateTime.now());
        task.setLastActivityAt(LocalDateTime.now());

        // Document 상태 업데이트
        task.getDocument().setStatus("PENDING_REVIEW");
        documentRepository.save(task.getDocument());

        TranslationTask saved = translationTaskRepository.save(task);
        log.info("번역 작업 제출: 작업 ID {}", taskId);
        return toResponse(saved);
    }

    @Transactional
    public TranslationTaskResponse abandonTask(Long taskId, Long translatorId) {
        TranslationTask task = translationTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("번역 작업을 찾을 수 없습니다: " + taskId));

        // 권한 체크
        if (!task.getTranslator().getId().equals(translatorId)) {
            throw new IllegalArgumentException("본인의 번역 작업만 포기할 수 있습니다.");
        }

        task.setStatus("ABANDONED");
        task.setLastActivityAt(LocalDateTime.now());

        // Document 상태 업데이트 (다른 작업자가 있으면 유지, 없으면 PENDING_TRANSLATION)
        long activeTasks = translationTaskRepository.findByDocument_IdAndStatus(
                task.getDocument().getId(), "IN_PROGRESS").size();
        if (activeTasks == 0) {
            task.getDocument().setStatus("PENDING_TRANSLATION");
            documentRepository.save(task.getDocument());
        }

        TranslationTask saved = translationTaskRepository.save(task);
        log.info("번역 작업 포기: 작업 ID {}", taskId);
        return toResponse(saved);
    }

    @Transactional
    public TranslationTaskResponse updateLastActivity(Long taskId, Long translatorId) {
        TranslationTask task = translationTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("번역 작업을 찾을 수 없습니다: " + taskId));

        // 권한 체크
        if (!task.getTranslator().getId().equals(translatorId)) {
            throw new IllegalArgumentException("본인의 번역 작업만 업데이트할 수 있습니다.");
        }

        task.setLastActivityAt(LocalDateTime.now());
        TranslationTask saved = translationTaskRepository.save(task);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TranslationTaskResponse> findAll() {
        return translationTaskRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TranslationTaskResponse> findByTranslatorId(Long translatorId) {
        return translationTaskRepository.findByTranslator_Id(translatorId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TranslationTaskResponse> findByTranslatorIdAndStatus(Long translatorId, String status) {
        return translationTaskRepository.findByTranslator_IdAndStatus(translatorId, status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TranslationTaskResponse> findByDocumentId(Long documentId) {
        return translationTaskRepository.findByDocument_Id(documentId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TranslationTaskResponse> findByStatus(String status) {
        return translationTaskRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<TranslationTaskResponse> findById(Long id) {
        return translationTaskRepository.findById(id)
                .map(this::toResponse);
    }

    private TranslationTaskResponse toResponse(TranslationTask task) {
        TranslationTaskResponse.TranslationTaskResponseBuilder builder = TranslationTaskResponse.builder()
                .id(task.getId())
                .status(task.getStatus())
                .startedAt(task.getStartedAt())
                .submittedAt(task.getSubmittedAt())
                .lastActivityAt(task.getLastActivityAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt());

        // Document 정보
        if (task.getDocument() != null) {
            builder.document(TranslationTaskResponse.DocumentInfo.builder()
                    .id(task.getDocument().getId())
                    .title(task.getDocument().getTitle())
                    .status(task.getDocument().getStatus())
                    .build());
        }

        // Translator 정보
        if (task.getTranslator() != null) {
            builder.translator(TranslationTaskResponse.TranslatorInfo.builder()
                    .id(task.getTranslator().getId())
                    .email(task.getTranslator().getEmail())
                    .name(task.getTranslator().getName())
                    .build());
        }

        // AssignedBy 정보
        if (task.getAssignedBy() != null) {
            builder.assignedBy(TranslationTaskResponse.AssignerInfo.builder()
                    .id(task.getAssignedBy().getId())
                    .email(task.getAssignedBy().getEmail())
                    .name(task.getAssignedBy().getName())
                    .build());
        }

        return builder.build();
    }
}

