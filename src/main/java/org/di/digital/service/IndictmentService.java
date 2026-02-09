package org.di.digital.service;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IndictmentService {
    SseEmitter generateIndictment(String caseNumber);
    Resource downloadIndictmentAsWord(String caseNumber);
    String getIndictment(String caseNumber);
}