package org.di.digital.reporting.service;

import org.di.digital.reporting.dto.response.ExportFile;
import org.di.digital.reporting.dto.response.ZonalConsolidatedResponse;
import org.di.digital.reporting.dto.response.ZonalSubmissionResponse;
import org.di.digital.reporting.dto.response.ZonalSubmissionSummaryResponse;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface ZonalReportAdminService {

    Page<ZonalSubmissionSummaryResponse> getHistory(Long templateId, Long regionId, LocalDate from, LocalDate to,
                                                    int page, int size);

    ZonalSubmissionResponse getSubmission(Long submissionId);

    ZonalConsolidatedResponse getConsolidated(Long templateId, LocalDate asOf);

    ExportFile exportConsolidated(Long templateId, LocalDate asOf);

}
