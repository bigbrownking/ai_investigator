package org.di.digital.reporting.service;

import org.di.digital.reporting.dto.request.ZonalTemplateRequest;
import org.di.digital.reporting.dto.response.ZonalTemplateResponse;
import org.di.digital.reporting.dto.response.ZonalTemplateSummaryResponse;

import java.util.List;

public interface ZonalTemplateAdminService {

    List<ZonalTemplateSummaryResponse> listTemplates();

    ZonalTemplateResponse getTemplate(Long templateId);

    ZonalTemplateResponse createTemplate(ZonalTemplateRequest request);

    ZonalTemplateResponse updateTemplate(Long templateId, ZonalTemplateRequest request);

    ZonalTemplateResponse setActive(Long templateId, boolean active);

    void deleteTemplate(Long templateId);
}
