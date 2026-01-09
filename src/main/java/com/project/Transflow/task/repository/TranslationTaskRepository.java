package com.project.Transflow.task.repository;

import com.project.Transflow.task.entity.TranslationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TranslationTaskRepository extends JpaRepository<TranslationTask, Long> {
    List<TranslationTask> findByTranslator_Id(Long translatorId);
    List<TranslationTask> findByTranslator_IdAndStatus(Long translatorId, String status);
    List<TranslationTask> findByDocument_Id(Long documentId);
    List<TranslationTask> findByStatus(String status);
    Optional<TranslationTask> findByDocument_IdAndTranslator_Id(Long documentId, Long translatorId);
    List<TranslationTask> findByDocument_IdAndStatus(Long documentId, String status);
}

