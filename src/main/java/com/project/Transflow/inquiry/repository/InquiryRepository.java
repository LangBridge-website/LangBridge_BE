package com.project.Transflow.inquiry.repository;

import com.project.Transflow.inquiry.entity.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Page<Inquiry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Inquiry> findByAuthor_IdOrderByCreatedAtDesc(Long authorId, Pageable pageable);

    /** 작성자 기준 문의 목록 (author.id) */
    List<Inquiry> findByAuthor_Id(Long authorId);

    @Query("SELECT COUNT(i) FROM Inquiry i WHERE NOT EXISTS (" +
            "SELECT 1 FROM InquiryReply r WHERE r.inquiry = i AND r.deletedAt IS NULL)")
    long countUnanswered();
}
