package org.di.digital.model.cases;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.enums.CaseMemberAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "case_member_history", indexes = {
        @Index(name = "idx_cmh_case_number", columnList = "case_number"),
        @Index(name = "idx_cmh_timestamp", columnList = "timestamp")
})
@EntityListeners(AuditingEntityListener.class)
public class CaseMemberHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaseMemberAction action;

    @Column(name = "case_number", nullable = false)
    private String caseNumber;

    @Column(name = "target_email", length = 100)
    private String targetEmail;

    @Column(name = "target_fio")
    private String targetFio;

    @Column(name = "performed_by_email", length = 100)
    private String performedByEmail;

    @Column(name = "performed_by_fio")
    private String performedByFio;
}