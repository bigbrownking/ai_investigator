package org.di.digital.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.Case;
import org.di.digital.model.enums.CaseActivityType;
import org.di.digital.repository.CaseRepository;
import org.di.digital.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.di.digital.util.UrlBuilder.qualificationUrl;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualificationServiceImpl implements QualificationService {

    private final CaseRepository caseRepository;
    private final ObjectMapper mapper;
    private final WordDocumentService wordDocumentService;
    private final CaseService caseService;
    private final StreamingService streamingService;
    private final LogService logService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${qualification.model.host}")
    private String pythonHost;

    @Value("${qualification.model.port}")
    private String pythonPort;

    @Override
    public SseEmitter generateQualification(String caseNumber) {
        SseEmitter emitter = new SseEmitter(0L);
        executor.execute(() -> streamQualification(caseNumber, emitter));
        return emitter;
    }

    private void streamQualification(String caseNumber, SseEmitter emitter) {
        streamingService.stream(
                qualificationUrl(pythonHost, pythonPort, caseNumber),
                List.of(),
                emitter,
                this::extractChunk,
                fullText -> {
                    saveQualification(caseNumber, fullText);
                    caseService.updateCaseActivity(caseNumber, CaseActivityType.QUALIFICATION_GENERATED.name());
                    log.info("Qualification streaming completed for case {}", caseNumber);
                },
                error -> log.error("Qualification streaming error for case {}", caseNumber, error)
        );
    }

    private String extractChunk(String chunk) {
        try {
            var node = mapper.readTree(chunk);
            if (node.has("delta"))  return node.get("delta").asText();
            if (node.has("answer")) return node.get("answer").asText();
        } catch (Exception ignored) {}
        return chunk;
    }

    private void saveQualification(String caseNumber, String text) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));
        entity.setQualification(text);
        caseRepository.save(entity);
    }

    @Override
    public Resource downloadQualificationAsWord(String caseNumber) {
        try {
            return new ByteArrayResource(
                    wordDocumentService.generateQualificationDocument(getQualification(caseNumber))
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getQualification(String caseNumber) {
        return caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found"))
                .getQualification();
    }
}