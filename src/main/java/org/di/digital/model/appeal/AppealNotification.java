package org.di.digital.model.appeal;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.enums.appeal.AppealNotificationType;
import org.di.digital.model.enums.appeal.AppealStatus;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "appeal_notifications")
@EntityListeners(AuditingEntityListener.class)
public class AppealNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email")
    private String userEmail;

    private Long appealId;
    private String applicantName;
    private String applicantSurname;

    @Enumerated(EnumType.STRING)
    private AppealNotificationType type;

    @Enumerated(EnumType.STRING)
    private AppealStatus appealStatus;

    private String reviewerName;

    private boolean isRead;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    public LocalDateTime createdAt;
}
