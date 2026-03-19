package org.di.digital.model.fl;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FLDocument {
    private String documentType;
    private String documentNumber;
    private LocalDate beginDate;
    private LocalDate endDate;
    private String issueOrg;
    private String invalidityReason;
    private LocalDate invalidityDate;
}