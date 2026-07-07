package org.di.digital.model.cases;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "case_analytics")
public class CaseAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false, unique = true)
    private Case caseEntity;

    @Column(name = "qualification_score_percent")
    private Double qualificationScorePercent;

    @Column(name = "qualification_summary", columnDefinition = "TEXT")
    private String qualificationSummary;

    /*@Column(name = "indictment_score_percent")
    private Double indictmentScorePercent;

    @Column(name = "indictment_summary", columnDefinition = "TEXT")
    private String indictmentSummary;*/

    @Column(name = "computed_at")
    private LocalDateTime computedAt;
}