package org.di.digital.dto.request.plan;

import lombok.Data;

@Data
public class ManualStatusRequest {
    private int actionNumber;
    private String newStatus;
    private String comment;
}