package org.di.digital.dto.response;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    private String documentType;
    private String documentNumber;
    private LocalDate beginDate;
    private LocalDate endDate;
    private String issueOrg;
    private String invalidityReason;
    private LocalDate invalidityDate;
}
