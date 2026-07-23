package org.di.digital.model.report;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.CaseFileStatusEnum;

import java.time.LocalDateTime;

@Entity
@Table(name = "case_reports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", unique = true)
    private Case caseEntity;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "report_file_url")
    private String reportFileUrl;

    @Column(name = "file_name")
    private String fileName;

    @Enumerated(EnumType.STRING)
    private CaseFileStatusEnum status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "processing_duration_seconds")
    private Long processingDurationSeconds;

    @Column(name = "user_email")
    private String userEmail;

    private LocalDateTime timestamp;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}