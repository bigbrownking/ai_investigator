package org.di.digital.dto.request.search;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSearchRequest {
    private String user;
    private String subject;
    private String module;
    private LocalDate from;
    private LocalDate to;
}