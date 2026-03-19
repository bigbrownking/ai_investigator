package org.di.digital.service;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IndictmentService {
    SseEmitter generateIndictment(String caseNumber, String email);
    Resource downloadIndictmentAsWord(String caseNumber, String email);
    String getIndictment(String caseNumber);
}