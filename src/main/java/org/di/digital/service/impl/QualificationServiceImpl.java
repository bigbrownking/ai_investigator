package org.di.digital.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.Case;
import org.di.digital.repository.CaseRepository;
import org.di.digital.service.QualificationService;
import org.di.digital.service.StreamingService;
import org.di.digital.service.WordDocumentService;
import org.di.digital.util.UrlBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualificationServiceImpl implements QualificationService {

    private static final String QUALIFICATION_ENDPOINT_TEMPLATE = "/workspaces/%s/generate-qualification?mode=hybrid";

    private final RestTemplate restTemplate;
    private final CaseRepository caseRepository;
    private final ObjectMapper mapper;
    private final StreamingService streamingService;
    private final WordDocumentService wordDocumentService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${qualification.model.host}")
    private String pythonHost;

    @Value("${qualification.model.port}")
    private String pythonPort;

    @Override
    public SseEmitter generateQualification(String caseNumber) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        executor.execute(() -> processQualificationAsync(caseNumber, emitter));
        return emitter;
    }

    @Override
    public Resource downloadQualificationAsWord(String caseNumber) {
        String qualification = getQualificationFromCase(caseNumber);

        try {
            byte[] docBytes = wordDocumentService.generateQualificationDocument(qualification);
            return new ByteArrayResource(docBytes);
        } catch (IOException e) {
            log.error("Error generating Word document for case: {}", caseNumber, e);
            throw new RuntimeException("Failed to generate Word document", e);
        }
    }

    private void processQualificationAsync(String caseNumber, SseEmitter emitter) {
        try {
            String rawResponse = fetchQualificationFromModel(caseNumber);

            if (rawResponse != null) {
                streamingService.streamText(emitter, rawResponse);
                String qualification = extractQualificationFromResponse(rawResponse);
                saveQualificationToCase(caseNumber, qualification);
            }

            emitter.complete();
            log.info("Qualification stream completed for case: {}", caseNumber);

        } catch (Exception e) {
            log.error("Error generating qualification for case: {}", caseNumber, e);
            emitter.completeWithError(e);
        }
    }

    private String fetchQualificationFromModel(String caseNumber) {
        String url = buildQualificationUrl(caseNumber);
        log.info("Requesting qualification from: {}", url);
        return restTemplate.postForObject(url, null, String.class);
    }

    private String extractQualificationFromResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return "";
        }

        try {
            JsonNode rootNode = mapper.readTree(rawResponse);
            return rootNode.has("answer") ? rootNode.get("answer").asText() : rawResponse;
        } catch (Exception e) {
            log.warn("Failed to parse JSON, returning raw text", e);
            return rawResponse;
        }
    }

    private void saveQualificationToCase(String caseNumber, String qualification) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        caseEntity.setQualification(qualification);
        caseRepository.save(caseEntity);

        log.info("Qualification saved for case: {}", caseNumber);
    }

    private String getQualificationFromCase(String caseNumber) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        String qualification = caseEntity.getQualification();

        if (qualification == null || qualification.isEmpty()) {
            throw new IllegalStateException("Qualification not found for case: " + caseNumber);
        }

        return qualification;
    }

    private String buildQualificationUrl(String caseNumber) {
        String endpoint = String.format(QUALIFICATION_ENDPOINT_TEMPLATE, caseNumber);
        return UrlBuilder.buildUrl(pythonHost, pythonPort, endpoint);
    }
}