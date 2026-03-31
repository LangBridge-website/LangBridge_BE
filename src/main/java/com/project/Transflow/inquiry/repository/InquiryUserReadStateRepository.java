package com.project.Transflow.inquiry.repository;

import com.project.Transflow.inquiry.entity.InquiryUserReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InquiryUserReadStateRepository extends JpaRepository<InquiryUserReadState, Long> {

    @Query("SELECT s FROM InquiryUserReadState s WHERE s.inquiry.id = :inquiryId AND s.user.id = :userId")
    Optional<InquiryUserReadState> findByInquiryAndUserIds(
            @Param("inquiryId") Long inquiryId,
            @Param("userId") Long userId);

    @Query("SELECT s FROM InquiryUserReadState s WHERE s.inquiry.id = :inquiryId")
    List<InquiryUserReadState> findAllByInquiryId(@Param("inquiryId") Long inquiryId);
}
