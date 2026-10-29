package org.di.digital.service.zonal;

import org.di.digital.dto.request.zonal.ZonalTemplateRequest;
import org.di.digital.dto.response.zonal.ZonalTemplateResponse;
import org.di.digital.dto.response.zonal.ZonalTemplateSummaryResponse;

import java.util.List;

public interface ZonalTemplateAdminService {

    List<ZonalTemplateSummaryResponse> listTemplates();

    ZonalTemplateResponse getTemplate(Long templateId);

    ZonalTemplateResponse createTemplate(ZonalTemplateRequest request);

    ZonalTemplateResponse updateTemplate(Long templateId, ZonalTemplateRequest request);

    ZonalTemplateResponse setActive(Long templateId, boolean active);

    void deleteTemplate(Long templateId);
}
