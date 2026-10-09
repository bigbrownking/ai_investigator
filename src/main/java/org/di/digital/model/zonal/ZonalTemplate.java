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
@Table(name = "reporting_zonal_templates",
        uniqueConstraints = @UniqueConstraint(name = "uq_reporting_zonal_templates_name", columnNames = "name_key"))
public class ZonalTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;
    @Column(name = "name_key", nullable = false)
    private String nameKey;
    @Column(columnDefinition = "text")
    private String description;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_definitions", columnDefinition = "jsonb", nullable = false)
    private List<ColumnDefinition> columns = new ArrayList<>();

    @Column(nullable = false)
    private boolean active;

    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Version
    private Long lockVersion;
}
