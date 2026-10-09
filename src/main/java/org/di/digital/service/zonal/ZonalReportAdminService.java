package org.di.digital.service.zonal;

import org.di.digital.dto.response.zonal.ExportFile;
import org.di.digital.dto.response.zonal.ZonalConsolidatedResponse;
import org.di.digital.dto.response.zonal.ZonalSubmissionResponse;
import org.di.digital.dto.response.zonal.ZonalSubmissionSummaryResponse;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface ZonalReportAdminService {

    Page<ZonalSubmissionSummaryResponse> getHistory(Long templateId, Long regionId, LocalDate from, LocalDate to,
                                                    int page, int size);

    ZonalSubmissionResponse getSubmission(Long submissionId);

    ZonalConsolidatedResponse getConsolidated(Long templateId, LocalDate asOf);

    ExportFile exportConsolidated(Long templateId, LocalDate asOf);

}
