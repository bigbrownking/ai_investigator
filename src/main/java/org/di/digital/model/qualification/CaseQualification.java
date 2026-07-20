package org.di.digital.model.qualification;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.cases.Case;

import java.time.LocalDateTime;

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

    private LocalDateTime generatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", unique = true)
    private Case caseEntity;
}