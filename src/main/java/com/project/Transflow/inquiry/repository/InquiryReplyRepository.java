package com.project.Transflow.inquiry.repository;

import com.project.Transflow.inquiry.entity.InquiryReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InquiryReplyRepository extends JpaRepository<InquiryReply, Long> {

    List<InquiryReply> findByInquiryIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long inquiryId);

    long countByInquiryIdAndDeletedAtIsNull(Long inquiryId);

    long countByInquiryIdAndDeletedAtIsNullAndCreatedAtAfter(Long inquiryId, java.time.LocalDateTime after);
}
