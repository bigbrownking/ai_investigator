package org.di.digital.dto.request.support;

import lombok.Data;

@Data
public class SupportTicketRequest {
    private String message;
    private String phoneNumber;
}
