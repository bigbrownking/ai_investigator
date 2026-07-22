package org.di.digital.service.indictment;

import org.di.digital.dto.request.indictment.IndictmentRephraseApplyRequest;
import org.di.digital.dto.request.indictment.IndictmentSectionUpdateRequest;
import org.di.digital.dto.response.indictment.IndictmentSectionDto;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface IndictmentService {
    SseEmitter generateIndictment(String caseNumber, String email);
    SseEmitter completeIndictment(String caseNumber, String email);
    SseEmitter generateIndictmentSection(String caseNumber, String email, int sectionId);
    SseEmitter generateIndictmentPrompt(String caseNumber, String email,
                                        int startSectionId, int startOffset,
                                        int endSectionId, int endOffset, String prompt);
    List<IndictmentSectionDto> applyRephrase(String caseNumber, IndictmentRephraseApplyRequest request);
    Resource downloadIndictmentAsWord(String caseNumber, String email);
    List<IndictmentSectionDto> getIndictmentSections(String caseNumber);
    IndictmentSectionDto updateSection(String caseNumber, IndictmentSectionUpdateRequest request);
}