package org.di.digital.model.cases;


import java.time.LocalDateTime;
import java.util.Optional;

import org.di.digital.model.enums.CaseRejectionReason;
import org.springframework.data.annotation.CreatedDate;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rejection_reason_status")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectionReasonStatus{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

     @Column(name = "user_id")
    private Long userId;

    @Column(name = "case_id")
    private Long caseId;

    @Column(name = "performed_by_fio")
    private String performedByFio;

    @Column(nullable = false)
    private boolean status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseRejectionReason rejectionReason;

    @CreatedDate
    @Column(name = "timestamp")
    private LocalDateTime timestamp;

  

}