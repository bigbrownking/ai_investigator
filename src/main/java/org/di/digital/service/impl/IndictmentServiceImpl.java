package org.di.digital.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.Case;
import org.di.digital.model.enums.CaseActivityType;
import org.di.digital.repository.CaseRepository;
import org.di.digital.service.CaseService;
import org.di.digital.service.IndictmentService;
import org.di.digital.service.StreamingService;
import org.di.digital.service.WordDocumentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.di.digital.util.RequestBodyBuilder.indictmentBody;
import static org.di.digital.util.UrlBuilder.indictmentUrl;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndictmentServiceImpl implements IndictmentService {

    private final CaseRepository caseRepository;
    private final ObjectMapper mapper;
    private final WordDocumentService wordDocumentService;
    private final CaseService caseService;
    private final StreamingService streamingService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${indictment.model.host}")
    private String pythonHost;

    @Value("${indictment.model.port}")
    private String pythonPort;

    @Override
    public SseEmitter generateIndictment(String caseNumber) {
        SseEmitter emitter = new SseEmitter(0L);
        executor.execute(() -> streamIndictment(caseNumber, emitter));
        return emitter;
    }

    private void streamIndictment(String caseNumber, SseEmitter emitter) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        if (entity.getQualificationsUploaded() == null || entity.getQualificationsUploaded().isEmpty()) {
            String message = "Qualification must be uploaded before generating indictment for case: " + caseNumber;
            log.warn(message);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }
        streamingService.stream(
                indictmentUrl(pythonHost, pythonPort),
                indictmentBody(caseNumber, entity.getQualificationsUploaded()),
                emitter,
                this::extractChunk,
                fullText -> {
                    saveIndictment(caseNumber, fullText);
                    caseService.updateCaseActivity(caseNumber, CaseActivityType.INDICTMENT_GENERATED.name());
                    log.info("Indictment streaming completed for case {}", caseNumber);
                },
                error -> log.error("Indictment streaming error for case {}", caseNumber, error)
        );
    }

    private String extractChunk(String chunk) {
        try {
            var node = mapper.readTree(chunk);
            if (node.has("delta"))  return node.get("delta").asText();
            if (node.has("result")) return node.get("result").asText();
        } catch (Exception ignored) {}
        return chunk;
    }

    private void saveIndictment(String caseNumber, String text) {
        if (text.isBlank()) return;
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));
        entity.setIndictment(text);
        caseRepository.save(entity);
    }

    @Override
    public Resource downloadIndictmentAsWord(String caseNumber) {
        try {
            return new ByteArrayResource(
                    wordDocumentService.generateIndictmentDocument(getIndictment(caseNumber))
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getIndictment(String caseNumber) {
        return caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found"))
                .getIndictment();
    }
}