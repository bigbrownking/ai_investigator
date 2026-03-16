package org.di.digital.model;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.enums.QAStatusEnum;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "case_interrogation_other_audios")
public class CaseInterrogationOtherAudio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(columnDefinition = "TEXT")
    private String audioFileUrl;

    @Enumerated(EnumType.STRING)
    private QAStatusEnum status;

    private Integer orderIndex;
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interrogation_id")
    private CaseInterrogation interrogation;
}
