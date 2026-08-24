package org.di.digital.dto.request.search;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketSearchRequest {
    private String user;
    private String message;
    private String phoneNumber;
    private LocalDate from;
    private LocalDate to;
}