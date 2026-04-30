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

    private String documentType;
    private String number;
    private String caseNumberState;
    private String fio;
    private String role;
    private LocalDate date;
    private String city;
    private String room;
    @Column(columnDefinition = "TEXT")
    private String addrezz;
    private String notificationNumber;
    private String notificationDate;
    private String state;
    private String involved;
    @Column(columnDefinition = "TEXT")
    private String involvedPersons;
    private String confession;
    @Column(columnDefinition = "TEXT")
    private String confessionText;
    private String language;
    private String translator;
    private String defender;
    private String familiarization;
    private String additionalInfo;
    @Column(columnDefinition = "TEXT")
    private String additionalText;
    private String application;
    private String investigator;
    private String investigatorProfession;
    private String investigatorAdministration;
    private String investigatorRegion;
    private String personYear;
    private String personTranslator;
    private String personSpecialist;
    private String testimony;
    private Boolean isDop;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "accumulated_seconds")
    @Builder.Default
    private Long accumulatedSeconds = 0L;

    @Column(name = "is_paused")
    @Builder.Default
    private Boolean isPaused = false;

    @OneToMany(mappedBy = "interrogation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InterrogationTimerSession> timerSessions = new ArrayList<>();

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_id", referencedColumnName = "id")
    private CaseInterrogationProtocol protocol;

    @Enumerated(EnumType.STRING)
    private CaseInterrogationStatusEnum status;

    @Builder.Default
    @OneToMany(mappedBy = "interrogation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseInterrogationApplicationFile> applicationFiles = new ArrayList<>();

    @OneToMany(mappedBy = "interrogation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<CaseInterrogationQA> qaList = new ArrayList<>();

    @OneToMany(mappedBy = "interrogation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<CaseInterrogationOtherAudio> otherAudios = new ArrayList<>();

    @OneToOne(mappedBy = "interrogation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CaseInterrogationChat chat;

    @ManyToOne
    @JoinColumn(name = "case_id")
    private Case caseEntity;
}
