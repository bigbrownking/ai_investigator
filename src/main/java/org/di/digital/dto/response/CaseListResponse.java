package org.di.digital.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseListResponse {
    private Long id;
    private String title;
    private String number;
    private boolean status;
    private int totalDocuments;
    private int totalPages;
    private int totalInterrogations;
    private long audioInterrogations;
    private LocalDateTime createdDate;
    private LocalDateTime lastActivityDate;
    private String lastActivityType;
    private String ownerEmail;
}
