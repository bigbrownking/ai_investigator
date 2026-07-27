package org.di.digital.dto.request.report;

import org.di.digital.model.enums.ReportRejectionReason;

import lombok.Data;


@Data
public class ReportStatusUpdateRequest{
    private ReportRejectionReason reportRejectionReason;
}