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
 * The single row of one region for one form and one date: saving a draft or resubmitting overwrites
 * the same row. {@code template} is the template version the row was last saved against; when a new
 * version is published, the next save moves the row to it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reporting_submissions",
        uniqueConstraints = @UniqueConstraint(name = "uq_reporting_submissions_template_region_date",
                columnNames = {"template_id", "region_id", "report_date"}),
        indexes = @Index(name = "ix_reporting_submissions_template_date",
                columnList = "template_id, report_date"))
public class RegionalSubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ReportTemplate template;
    @Column(nullable = false)
    private Long regionId;
    @Column(nullable = false)
    private LocalDate reportDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubmissionStatus status;

    /**
     * Flat row: columnKey -> value, e.g. {"damage": 83678242, "compensated": 82958242}.
     * Raw operator input while DRAFT, normalized by column type once SUBMITTED.
     */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> cells = new HashMap<>();

    /** User who saved or submitted last. */
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
