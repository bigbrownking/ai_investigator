package org.di.digital.dto.request.search;

import lombok.*;

import java.time.LocalDate;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchRequest {
    private String iin;
    private String fio;
    private String email;
    private String profession;
    private String administration;
    private String region;
    private Boolean active;
    private LocalDate from;
    private LocalDate to;
}
