package org.di.digital.reporting.model;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.reporting.model.enums.SubmissionStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * The single row of one region for one form and one date. Unique by (templateCode, regionId, reportDate):
 * saving a draft or resubmitting overwrites the same row.
 * regionId and operatorId are plain ids from Postgres users/regions, deliberately without FK.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reporting_submissions",
        uniqueConstraints = @UniqueConstraint(name = "uq_reporting_submissions_code_region_date",
                columnNames = {"template_code", "region_id", "report_date"}),
        indexes = @Index(name = "ix_reporting_submissions_code_date",
                columnList = "template_code, report_date"))
public class RegionalSubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String templateCode;
    @Column(nullable = false)
    private Long regionId;
    @Column(nullable = false)
    private LocalDate reportDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubmissionStatus status;

    /**
     * Flat row: columnKey -> value, e.g. {"col_1": 1500, "col_2": "IN_PROGRESS"}.
     * Raw operator input while DRAFT, normalized by column type once SUBMITTED.
     */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> cells = new HashMap<>();

    /** Template version the cells were last saved against. */
    private Integer templateVersion;
    /** Operator who saved or submitted last. */
    private Long operatorId;
    private LocalDateTime submittedAt;
    @Builder.Default
    @Column(nullable = false)
    private Integer submitCount = 0;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Version
    private Long lockVersion;
}
