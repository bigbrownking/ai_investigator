package org.di.digital.model.plan;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.PlanStatus;
import org.di.digital.model.user.User;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "plan_approval_history", indexes = {
        @Index(name = "idx_pah_case_id", columnList = "case_id")
})
@EntityListeners(AuditingEntityListener.class)
public class PlanApprovalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Case caseEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private PlanStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private PlanStatus toStatus;

    @Column(name = "approval_level", nullable = false)
    private int approvalLevel;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreatedDate
    @Column(name = "reviewed_at", updatable = false)
    private LocalDateTime reviewedAt;


}