package org.di.digital.dto.request;

import lombok.Data;

@Data
public class SupportTicketRequest {
    private String message;
    private String phoneNumber;
}
