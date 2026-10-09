package org.di.digital.service.zonal;

import org.di.digital.dto.request.zonal.ZonalRowsRequest;
import org.di.digital.dto.response.zonal.ZonalFormResponse;
import org.di.digital.dto.response.zonal.ZonalFormSummaryResponse;
import org.di.digital.dto.response.zonal.ZonalSubmissionResponse;
import org.di.digital.dto.response.zonal.ZonalSubmissionSummaryResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ZonalTableService {

    List<ZonalFormSummaryResponse> listForms();

    ZonalFormResponse getForm(Long templateId);

    ZonalFormResponse saveRows(Long templateId, ZonalRowsRequest request);

    ZonalSubmissionResponse submit(Long templateId, ZonalRowsRequest request);

    Page<ZonalSubmissionSummaryResponse> getHistory(Long templateId, int page, int size);

    ZonalSubmissionResponse getSubmission(Long submissionId);
}
