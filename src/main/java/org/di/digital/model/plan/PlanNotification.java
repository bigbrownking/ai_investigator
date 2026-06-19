package org.di.digital.model.plan;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.enums.PlanNotificationType;
import org.di.digital.model.enums.PlanStatus;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "plan_notifications")
@EntityListeners(AuditingEntityListener.class)
public class PlanNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventId;

    @Column(name = "user_email")
    private String userEmail;

    @Enumerated(EnumType.STRING)
    private PlanNotificationType type;

    private Long caseId;
    private String caseNumber;
    private String caseTitle;

    @Enumerated(EnumType.STRING)
    private PlanStatus planStatus;

    private int approvalLevel;
    private String reviewerName;
    private String reviewerProfession;

    @Column(columnDefinition = "TEXT")
    private String comment;

    private boolean isRead;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
