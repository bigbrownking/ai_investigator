package org.di.digital.model.qualification;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.cases.Case;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "case_qualifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseQualification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "sections", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Map<String, Object>> sections;

    private LocalDateTime generatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", unique = true)
    private Case caseEntity;
}