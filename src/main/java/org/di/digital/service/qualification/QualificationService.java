package org.di.digital.service.qualification;

import org.di.digital.dto.response.qualification.QualificationSectionDto;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface QualificationService {
    SseEmitter generateQualification(String workspaceId, String email);
    Resource downloadQualificationAsWord(String caseNumber, String email);
    List<QualificationSectionDto> getQualificationSections(String caseNumber);
}
