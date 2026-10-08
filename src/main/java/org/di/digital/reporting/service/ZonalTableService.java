package org.di.digital.reporting.service;

import org.di.digital.reporting.dto.request.ZonalRowsRequest;
import org.di.digital.reporting.dto.response.ZonalFormResponse;
import org.di.digital.reporting.dto.response.ZonalFormSummaryResponse;
import org.di.digital.reporting.dto.response.ZonalSubmissionResponse;
import org.di.digital.reporting.dto.response.ZonalSubmissionSummaryResponse;
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
