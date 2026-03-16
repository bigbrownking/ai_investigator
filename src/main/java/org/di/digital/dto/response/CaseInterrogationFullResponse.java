package org.di.digital.dto.response;

import lombok.*;
import org.di.digital.model.CaseInterrogationQA;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseInterrogationFullResponse {
    private long id;
    private String city;
    private String room;
    private String addrezz;
    private String notificationNumber;
    private String notificationDate;
    private String caseNumberState;
    private String state;
    private String caseNumber;
    private String documentType;
    private String number;
    private String fio;
    private String role;
    private LocalDate date;
    private String involved;
    private String involvedPersons;
    private String confession;
    private String confessionText;
    private String language;
    private String translator;
    private String defender;
    private String familiarization;
    private String additionalInfo;
    private String additionalText;
    private String application;
    private String investigator;
    private String investigatorProfession;
    private String investigatorRegion;
    private CaseInterrogationProtocolResponse protocol;
    private List<CaseInterrogationQAResponse> qaList;
    private List<CaseInterrogationApplicationFileResponse> applications;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationSeconds;
    private List<InterrogationTimerSessionResponse> timerSessions;
    private String status;

}
