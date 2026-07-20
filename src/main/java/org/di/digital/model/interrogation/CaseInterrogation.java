package org.di.digital.model.interrogation;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.CaseInterrogationStatusEnum;
import org.di.digital.model.enums.InterrogationLimitProfile;
import org.di.digital.model.enums.InterrogationSpecialGround;
import org.springframework.data.annotation.CreatedDate;

import java.time.Duration;
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
    private String lawyer;
    private String state;
    private String involved;

    @Builder.Default
    @OneToMany(mappedBy = "interrogation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseInterrogationInvolvedPersons> involvedPersons = new ArrayList<>();

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
    private List<CaseInterrogationTimerSession> timerSessions = new ArrayList<>();

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

    @OneToOne(mappedBy = "interrogation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CaseInterrogationCaseChat caseChat;

    @Enumerated(EnumType.STRING)
    @Column(name = "special_ground")
    @Builder.Default
    private InterrogationSpecialGround specialGround = InterrogationSpecialGround.NONE;

    @Column(name = "special_ground_note")
    private String specialGroundNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_profile")
    private InterrogationLimitProfile limitProfile;

    @Column(name = "category_confirmed")
    @Builder.Default
    private Boolean categoryConfirmed = false;

    @Column(name = "break_started_at")
    private LocalDateTime breakStartedAt;

    @Column(name = "on_break")
    @Builder.Default
    private Boolean onBreak = false;

    @Column(name = "continuous_override_confirmed")
    @Builder.Default
    private Boolean continuousOverrideConfirmed = false;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @ManyToOne
    @JoinColumn(name = "case_id")
    private Case caseEntity;

    @Column(name = "notified_continuous_warn")
    @Builder.Default
    private Boolean notifiedContinuousWarn = false;

    @Column(name = "notified_continuous_limit")
    @Builder.Default
    private Boolean notifiedContinuousLimit = false;

    @Column(name = "notified_break_over")
    @Builder.Default
    private Boolean notifiedBreakOver = false;

    @Column(name = "notified_daily_warn")
    @Builder.Default
    private Boolean notifiedDailyWarn = false;

    @Column(name = "notified_daily_limit")
    @Builder.Default
    private Boolean notifiedDailyLimit = false;

    @Column(name = "current_series_started_at")
    private LocalDateTime currentSeriesStartedAt;

    public static final Duration MANDATORY_BREAK = Duration.ofMinutes(2);

    public boolean isAudioUsed() {
        return qaList.stream()
                .anyMatch(qa -> !qa.getAudioRecords().isEmpty() || !qa.getAudioFileUrl().isEmpty());
    }
    public String getAdequateLanguage(){
       return language.equals("русском") ? "russian" : language.equals("казахском") ? "kazakh" : null;
    }
}
