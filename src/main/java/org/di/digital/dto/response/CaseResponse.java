package org.di.digital.dto.response;


import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseResponse {
    private Long id;
    private String title;
    private String number;
    private String description;
    private boolean status;

    private List<CaseUserResponse> users;

    private List<CaseFileResponse> files;
    private List<CaseInterrogationResponse> interrogations;

    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    private LocalDateTime lastActivityDate;
    private String lastActivityType;
    private LocalDateTime qualificationGeneratedAt;
    private LocalDateTime indictmentGeneratedAt;

    private String ownerEmail;
}
