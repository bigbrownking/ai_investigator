package org.di.digital.dto.request.search;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppealSearchRequest {
    private String from;
    private String to;
    private String status;
    private String region;
    private LocalDate createdAt;
}
