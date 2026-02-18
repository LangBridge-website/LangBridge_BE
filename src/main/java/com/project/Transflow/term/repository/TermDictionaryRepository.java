package com.project.Transflow.term.repository;

import com.project.Transflow.term.entity.TermDictionary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TermDictionaryRepository extends JpaRepository<TermDictionary, Long> {
    List<TermDictionary> findBySourceLangAndTargetLang(String sourceLang, String targetLang);
    Page<TermDictionary> findBySourceLangAndTargetLang(String sourceLang, String targetLang, Pageable pageable);
    List<TermDictionary> findBySourceLang(String sourceLang);
    Page<TermDictionary> findBySourceLang(String sourceLang, Pageable pageable);
    List<TermDictionary> findByTargetLang(String targetLang);
    Page<TermDictionary> findByTargetLang(String targetLang, Pageable pageable);
    Page<TermDictionary> findAll(Pageable pageable);
    Optional<TermDictionary> findBySourceTermAndSourceLangAndTargetLang(String sourceTerm, String sourceLang, String targetLang);
    List<TermDictionary> findByCreatedBy_Id(Long createdById);
    boolean existsBySourceTermAndSourceLangAndTargetLang(String sourceTerm, String sourceLang, String targetLang);
}

