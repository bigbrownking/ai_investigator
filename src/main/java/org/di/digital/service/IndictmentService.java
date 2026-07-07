package org.di.digital.service;

import org.di.digital.dto.request.indictment.IndictmentSectionUpdateRequest;
import org.di.digital.dto.response.indictment.IndictmentSectionDto;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface IndictmentService {
    SseEmitter generateIndictment(String caseNumber, String language, String email);
    SseEmitter completeIndictment(String caseNumber, String language, String email);
    SseEmitter generateIndictmentSection(String caseNumber, String language, String email, int sectionId);
    Resource downloadIndictmentAsWord(String caseNumber, String email);
    String getIndictment(String caseNumber);
    List<IndictmentSectionDto> getIndictmentSections(String caseNumber);
    IndictmentSectionDto updateSection(String caseNumber, IndictmentSectionUpdateRequest request);
}