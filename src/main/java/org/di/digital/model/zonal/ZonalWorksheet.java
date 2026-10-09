package org.di.digital.model.zonal;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reporting_zonal_worksheets",
        uniqueConstraints = @UniqueConstraint(name = "uq_reporting_zonal_worksheets_template_region",
                columnNames = {"template_id", "region_id"}))
public class ZonalWorksheet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;
    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_rows", columnDefinition = "jsonb", nullable = false)
    private List<TableRow> rows = new ArrayList<>();

    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastSubmittedAt;

    @Version
    private Long lockVersion;
}
