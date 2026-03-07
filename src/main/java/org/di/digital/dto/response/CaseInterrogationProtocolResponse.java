package org.di.digital.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseInterrogationProtocolResponse {
    private Long interrogationId;
    private String fio;
    private String dateOfBirth;
    private String birthPlace;
    private String citizenship;
    private String nationality;
    private String education;
    private String martialStatus;
    private String workOrStudyPlace;
    private String position;
    private String address;
    private String contactPhone;
    private String contactEmail;
    private String other;
    private String relation;
    private String technical;
    private String military;
    private String criminalRecord;
    private String iinOrPassport;
}
