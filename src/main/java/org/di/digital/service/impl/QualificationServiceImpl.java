package org.di.digital.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.Case;
import org.di.digital.model.enums.CaseActivityType;
import org.di.digital.repository.CaseRepository;
import org.di.digital.service.CaseService;
import org.di.digital.service.QualificationService;
import org.di.digital.service.WordDocumentService;
import org.di.digital.util.UrlBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualificationServiceImpl implements QualificationService {

    private static final String QUALIFICATION_ENDPOINT_TEMPLATE =
            "/workspaces/%s/generate-qualification?mode=hybrid&stream=false";

    private final WebClient.Builder webClientBuilder;
    private final CaseRepository caseRepository;
    private final ObjectMapper mapper;
    private final WordDocumentService wordDocumentService;
    private final CaseService caseService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${qualification.model.host}")
    private String pythonHost;

    @Value("${qualification.model.port}")
    private String pythonPort;

    @Override
    public SseEmitter generateQualification(String caseNumber) {
        SseEmitter emitter = new SseEmitter(0L);
        executor.execute(() -> streamFromModel(caseNumber, emitter));
        return emitter;
    }

    private void streamFromModel(String caseNumber, SseEmitter emitter) {
        String url = buildQualificationUrl(caseNumber);
        StringBuilder fullText = new StringBuilder();

        log.info("Streaming qualification from model: {}", url);

        webClientBuilder.build()
                .post()
                .uri(url)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .subscribe(
                        chunk -> {
                            try {
                                log.info("chunk at {} : {}", System.currentTimeMillis(), chunk);
                                String text = extractChunk(chunk);
                                fullText.append(text);
                                emitter.send(SseEmitter.event().data(text));
                            } catch (Exception e) {
                                log.error("Error sending SSE chunk", e);
                            }
                        },
                        error -> {
                            log.error("Streaming error", error);
                            emitter.completeWithError(error);
                        },
                        () -> {
                            saveQualification(caseNumber, fullText.toString());

                            caseService.updateCaseActivity(caseNumber, CaseActivityType.QUALIFICATION_GENERATED.name());

                            emitter.complete();
                            log.info("Streaming completed for case {}", caseNumber);
                        }
                );
    }

    private String extractChunk(String chunk) {
        try {
            JsonNode node = mapper.readTree(chunk);
            if (node.has("delta")) {
                return node.get("delta").asText();
            }
            if (node.has("answer")) {
                return node.get("answer").asText();
            }
        } catch (Exception ignored) {
        }
        return chunk;
    }

    private void saveQualification(String caseNumber, String text) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));
        entity.setQualification(text);
        caseRepository.save(entity);
    }

    private String buildQualificationUrl(String caseNumber) {
        String endpoint = String.format(QUALIFICATION_ENDPOINT_TEMPLATE, caseNumber);
        return UrlBuilder.buildUrl(pythonHost, pythonPort, endpoint);
    }

    @Override
    public Resource downloadQualificationAsWord(String caseNumber) {
        String qualification = getQualification(caseNumber);

        try {
            return new ByteArrayResource(
                    wordDocumentService.generateQualificationDocument(qualification)
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
