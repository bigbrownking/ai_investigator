package org.di.digital.reporting.service;

import org.di.digital.reporting.dto.request.CreateTemplateRequest;
import org.di.digital.reporting.dto.request.UpdateTemplateRequest;
import org.di.digital.reporting.dto.response.ActiveFormResponse;
import org.di.digital.reporting.dto.response.TemplateResponse;
import org.di.digital.reporting.dto.response.TemplateSummaryResponse;

import java.util.List;

/**
 * Report form lifecycle: DRAFT -> ACTIVE -> ARCHIVED.
 * A form has at most one DRAFT and one ACTIVE version; ACTIVE versions are never edited.
 */
public interface TemplateService {

    List<TemplateSummaryResponse> listForms();

    /** Forms that have an active version, i.e. can be filled in by operators. */
    List<ActiveFormResponse> listActiveForms();

    List<TemplateResponse> getVersions(String code);

    TemplateResponse getVersion(String code, Integer version);

    TemplateResponse getActive(String code);

    /** New form, version 1 as DRAFT. */
    TemplateResponse createForm(CreateTemplateRequest request);

    /** New DRAFT version copied from the latest version of the form. */
    TemplateResponse createDraft(String code);

    TemplateResponse updateDraft(String code, Integer version, UpdateTemplateRequest request);

    void deleteDraft(String code, Integer version);

    /** Makes the DRAFT version ACTIVE and archives the previously active one. */
    TemplateResponse publish(String code, Integer version);
}
