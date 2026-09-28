package org.di.digital.reporting.model;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.reporting.model.enums.TemplateStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Structure of one consolidated table: its columns only. Rows are regions, one per region,
 * in the fixed order given by RegionDirectory.
 * {@code code} identifies the form, {@code version} grows on every change: an ACTIVE template is never
 * edited in place. "One ACTIVE / one DRAFT per code" is enforced by partial indexes, see ReportingSchemaInitializer.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reporting_templates",
        uniqueConstraints = @UniqueConstraint(name = "uq_reporting_templates_code_version",
                columnNames = {"code", "version"}))
public class ReportTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;
    @Column(nullable = false)
    private Integer version;
    @Column(nullable = false)
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TemplateStatus status;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_definitions", columnDefinition = "jsonb", nullable = false)
    private List<ColumnDefinition> columns = new ArrayList<>();

    /** null for templates seeded by the system. */
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime archivedAt;

    @Version
    private Long lockVersion;
}
