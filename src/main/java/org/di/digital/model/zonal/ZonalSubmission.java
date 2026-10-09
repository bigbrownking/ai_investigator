package org.di.digital.model.zonal;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reporting_zonal_submissions",
        indexes = {
                @Index(name = "ix_reporting_zonal_submissions_region", columnList = "region_id, submitted_at"),
                @Index(name = "ix_reporting_zonal_submissions_template", columnList = "template_id, region_id, submitted_at")
        })
public class ZonalSubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;
    @Column(name = "region_id", nullable = false)
    private Long regionId;
    @Column(nullable = false)
    private String name;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_definitions", columnDefinition = "jsonb", nullable = false)
    private List<ColumnDefinition> columns = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "table_rows", columnDefinition = "jsonb", nullable = false)
    private List<TableRow> rows = new ArrayList<>();

    @Column(nullable = false)
    private Integer rowCount;
    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
    @Column(nullable = false)
    private Long submittedBy;
    @Column(columnDefinition = "text")
    private String comment;
}
