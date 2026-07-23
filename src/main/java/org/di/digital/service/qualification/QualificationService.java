package org.di.digital.service.qualification;

import org.di.digital.dto.request.qualification.QualificationRephraseApplyRequest;
import org.di.digital.dto.request.qualification.QualificationSectionUpdateRequest;
import org.di.digital.dto.response.qualification.QualificationSectionDto;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface QualificationService {
    SseEmitter generateQualification(String caseNumber, String email);
    Resource downloadQualificationAsWord(String caseNumber, String email);
    List<QualificationSectionDto> getQualificationSections(String caseNumber);
    SseEmitter generateQualificationSection(String caseNumber, String email, int sectionId);

    SseEmitter generateQualificationPrompt(String caseNumber, String email,
                                           int startSectionId, int startOffset,
                                           int endSectionId, int endOffset, String prompt);

    List<QualificationSectionDto> applyRephrase(String caseNumber, QualificationRephraseApplyRequest request);

    QualificationSectionDto updateSection(String caseNumber, QualificationSectionUpdateRequest request);
}
