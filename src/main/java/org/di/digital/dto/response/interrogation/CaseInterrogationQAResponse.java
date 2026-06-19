package org.di.digital.dto.response.interrogation;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseInterrogationQAResponse {
    private long id;
    private long interrogationId;
    private String question;
    private String answer;
    private String status;
    private LocalDateTime createAt;
    private List<AudioRecordResponse> audioRecords;

}
