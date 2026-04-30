package org.di.digital.dto.request.search;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseSearchRequest {
    private String number;
    private String title;
    private Boolean status;
    private LocalDate createdFrom;
    private LocalDate createdTo;
    private String ownerName;
    private String region;
}
