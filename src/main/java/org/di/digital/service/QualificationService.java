package org.di.digital.service;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface QualificationService {
    SseEmitter generateQualification(String workspaceId);
    Resource downloadQualificationAsWord(String caseNumber);
    String getQualification(String caseNumber);
}
