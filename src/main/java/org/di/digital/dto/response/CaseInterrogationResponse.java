package org.di.digital.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseInterrogationResponse {
    private Long id;
    private String documentType;
    private String number;
    private String fio;
    private String role;
    private String date;
    private String status;
}
