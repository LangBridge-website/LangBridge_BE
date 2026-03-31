package com.project.Transflow.inquiry.entity;

import com.project.Transflow.user.entity.User;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 문의 작성자가 상세를 열었을 때 마지막으로 읽은 시각 (새 답변 배지용)
 */
@Entity
@Table(name = "inquiry_user_read_state", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"inquiry_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InquiryUserReadState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inquiry_id", nullable = false)
    private Inquiry inquiry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, name = "last_read_at")
    private LocalDateTime lastReadAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
