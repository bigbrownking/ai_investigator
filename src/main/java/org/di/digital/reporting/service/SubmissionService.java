package org.di.digital.reporting.service;

import org.di.digital.reporting.dto.request.ReportRowRequest;
import org.di.digital.reporting.dto.response.OperatorFormResponse;

import java.time.LocalDate;

/**
 * Operator side: one row per form, region and date. The region always comes from the current
 * user, never from the request. Only today's row can be saved or (re)submitted.
 */
public interface SubmissionService {

    /** Template columns plus this region's row for the date (today when null; empty row when nothing was saved). */
    OperatorFormResponse getForm(String templateCode, LocalDate reportDate);

    /** Saves the row as DRAFT with shape-only validation. Not allowed once the row is SUBMITTED. */
    OperatorFormResponse saveDraft(String templateCode, ReportRowRequest request);

    /** Validates types strictly, normalizes values and marks the row SUBMITTED; resubmission overwrites. */
    OperatorFormResponse submit(String templateCode, ReportRowRequest request);
}
