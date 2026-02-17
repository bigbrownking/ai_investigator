package org.di.digital.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.Case;
import org.di.digital.model.enums.CaseActivityType;
import org.di.digital.repository.CaseRepository;
import org.di.digital.service.CaseService;
import org.di.digital.service.IndictmentService;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndictmentServiceImpl implements IndictmentService {

    private static final String INDICTMENT_ENDPOINT = "/generate_akt";

    private final WebClient.Builder webClientBuilder;
    private final CaseRepository caseRepository;
    private final ObjectMapper mapper;
    private final WordDocumentService wordDocumentService;
    private final CaseService caseService;


    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${indictment.model.host}")
    private String pythonHost;

    @Value("${indictment.model.port}")
    private String pythonPort;

    @Override
    public SseEmitter generateIndictment(String caseNumber) {
        SseEmitter emitter = new SseEmitter(0L);
        executor.execute(() -> streamFromModel(caseNumber, emitter));
        return emitter;
    }

    private void streamFromModel(String caseNumber, SseEmitter emitter) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        if(!entity.hasQualificationUploaded()){
            String message = "Qualification must be uploaded before generating indictment for case: " + caseNumber;
            log.warn(message);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }
        String url = buildIndictmentUrl();
        Map<String, Object> body = buildRequestBody(caseNumber);

        StringBuilder fullText = new StringBuilder();

        log.info("Streaming indictment from {}", url);

        webClientBuilder.build()
                .post()
                .uri(url)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
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
                            saveIndictment(caseNumber, fullText.toString());

                            caseService.updateCaseActivity(caseNumber, CaseActivityType.INDICTMENT_GENERATED.name());

                            emitter.complete();
                            log.info("Indictment streaming completed for case {}", caseNumber);
                        }
                );
    }

    private String extractChunk(String chunk) {
        try {
            JsonNode node = mapper.readTree(chunk);
            if (node.has("delta")) {
                return node.get("delta").asText();
            }
            if (node.has("result")) {
                return node.get("result").asText();
            }
        } catch (Exception ignored) {
        }
        return chunk;
    }

    private void saveIndictment(String caseNumber, String text) {
        if (text.isBlank()) return;

        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        entity.setIndictment(text);
        caseRepository.save(entity);
    }

    private String buildIndictmentUrl() {
        return UrlBuilder.buildUrl(pythonHost, pythonPort, INDICTMENT_ENDPOINT);
    }

    private Map<String, Object> buildRequestBody(String caseNumber) {
        Map<String, Object> body = new HashMap<>();
        body.put("working_dir", caseNumber);
        body.put("mode", "hybrid");
        body.put("stream", false);
        return body;
    }

    @Override
    public Resource downloadIndictmentAsWord(String caseNumber) {
        String indictment = getIndictment(caseNumber);

        try {
            return new ByteArrayResource(
                    wordDocumentService.generateIndictmentDocument(indictment)
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
