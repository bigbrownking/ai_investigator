package org.di.digital.dto.response.cases;

import java.time.LocalDateTime;

import org.di.digital.model.enums.CaseRejectionReason;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectionReasonResponse{
    private Long id;
    private String caseNumber;
    private boolean status;
    private CaseRejectionReason rejectionReason;
    private LocalDateTime timestamp;

}