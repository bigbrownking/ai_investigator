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
    private String caseNumber;
    private String iin;
    private String fio;
    private String role;
    private LocalDate date;
    private CaseInterrogationProtocolResponse protocol;
    private List<CaseInterrogationQAResponse> qaList;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationSeconds;
    private List<InterrogationTimerSessionResponse> timerSessions;
    private String status;

}
