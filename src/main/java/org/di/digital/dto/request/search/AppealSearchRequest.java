package org.di.digital.dto.request.search;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppealSearchRequest {
    private String cameFrom;
    private String cameTo;
    private String status;
    private String region;
    private LocalDate from;
    private LocalDate to;
}
