package org.di.digital.model;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.enums.CaseInterrogationStatusEnum;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "case_interrogations")
public class CaseInterrogation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String iin;
    private String fio;
    private String role;
    private LocalDate date;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "accumulated_seconds")
    @Builder.Default
    private Long accumulatedSeconds = 0L;

    @OneToMany(mappedBy = "interrogation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startedAt ASC")
    @Builder.Default
    private List<InterrogationTimerSession> timerSessions = new ArrayList<>();

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_id", referencedColumnName = "id")
    private CaseInterrogationProtocol protocol;

    @Enumerated(EnumType.STRING)
    private CaseInterrogationStatusEnum status;

    @OneToMany(mappedBy = "interrogation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<CaseInterrogationQA> qaList = new ArrayList<>();

    @OneToOne(mappedBy = "interrogation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CaseInterrogationChat chat;

    @ManyToOne
    @JoinColumn(name = "case_id")
    private Case caseEntity;
}
