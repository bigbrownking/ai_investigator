package org.di.digital.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportProcessingMessage {
    private String caseNumber;
    private Long userId;
    private String userEmail;
}