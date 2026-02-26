package org.di.digital.dto.response;

import lombok.*;

import java.time.LocalDateTime;

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
}
