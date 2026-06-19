package org.di.digital.model.interrogation;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.enums.QAStatusEnum;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "case_interrogation_qa")
public class CaseInterrogationQA {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(columnDefinition = "TEXT")
    private String audioFileUrl;

    private Boolean isEdited;

    @Enumerated(EnumType.STRING)
    private QAStatusEnum status;

    private Integer orderIndex;
    private LocalDateTime createdAt;

    @Builder.Default
    private Boolean manuallyEdited = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interrogation_id")
    private CaseInterrogation interrogation;

    @OneToMany(mappedBy = "qa", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("createdAt ASC")
    private List<CaseInterrogationAudioRecord> audioRecords = new ArrayList<>();
}
