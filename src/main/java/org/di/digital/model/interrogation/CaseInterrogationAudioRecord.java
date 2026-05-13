package org.di.digital.model.interrogation;

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
@Table(name = "case_interrogation_audio_records")
public class CaseInterrogationAudioRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String audioFileUrl;

    @Column(columnDefinition = "TEXT")
    private String transcribedText;

    @Enumerated(EnumType.STRING)
    private QAStatusEnum status;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qa_id")
    private CaseInterrogationQA qa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "other_audio_id")
    private CaseInterrogationOtherAudio otherAudio;
}
