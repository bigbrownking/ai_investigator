package org.di.digital.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.Case;
import org.di.digital.repository.CaseRepository;
import org.di.digital.service.IndictmentService;
import org.di.digital.service.StreamingService;
import org.di.digital.service.WordDocumentService;
import org.di.digital.util.UrlBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
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

    private final RestTemplate restTemplate;
    private final CaseRepository caseRepository;
    private final ObjectMapper mapper;
    private final StreamingService streamingService;
    private final WordDocumentService wordDocumentService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${indictment.model.host}")
    private String pythonHost;

    @Value("${indictment.model.port}")
    private String pythonPort;

    @Override
    public SseEmitter generateIndictment(String caseNumber) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        executor.execute(() -> processIndictmentAsync(caseNumber, emitter));
        return emitter;
    }

    @Override
    public Resource downloadIndictmentAsWord(String caseNumber) {
        String indictment = getIndictmentFromCase(caseNumber);

        try {
            byte[] docBytes = wordDocumentService.generateIndictmentDocument(indictment);
            return new ByteArrayResource(docBytes);
        } catch (IOException e) {
            log.error("Error generating Word document for case: {}", caseNumber, e);
            throw new RuntimeException("Failed to generate Word document", e);
        }
    }

    private void processIndictmentAsync(String caseNumber, SseEmitter emitter) {
        try {
            String rawResponse = fetchIndictmentFromModel(caseNumber);

            if (rawResponse != null) {
                log.debug("Raw response length: {}", rawResponse.length());
                log.debug("Raw response preview: {}", rawResponse.substring(0, Math.min(200, rawResponse.length())));

                String indictment = extractIndictmentFromResponse(rawResponse);
                log.info("Extracted indictment length: {}", indictment.length());
                log.debug("Extracted indictment preview: {}", indictment.substring(0, Math.min(200, indictment.length())));

                if (indictment != null && !indictment.isEmpty()) {
                    streamingService.streamText(emitter, indictment);
                    saveIndictmentToCase(caseNumber, indictment);
                } else {
                    log.error("Extracted indictment is empty!");
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("Failed to extract indictment from response"));
                }
            }

            emitter.complete();
            log.info("Indictment stream completed for case: {}", caseNumber);

        } catch (Exception e) {
            log.error("Error generating indictment for case: {}", caseNumber, e);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("Error: " + e.getMessage()));
            } catch (IOException ioException) {
                log.error("Error sending error event", ioException);
            }
            emitter.completeWithError(e);
        }
    }

    private String fetchIndictmentFromModel(String caseNumber) {
        String url = buildIndictmentUrl();
        Map<String, Object> requestBody = buildRequestBody(caseNumber);

        log.info("Requesting indictment from: {} with body: {}", url, requestBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                String.class
        );

        String responseBody = response.getBody();
        log.debug("Response status: {}", response.getStatusCode());
        log.debug("Response body length: {}", responseBody != null ? responseBody.length() : 0);

        return responseBody;
    }

    private String extractIndictmentFromResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            log.warn("Raw response is null or empty");
            return "";
        }

        try {
            JsonNode rootNode = mapper.readTree(rawResponse);
            log.debug("JSON parsed successfully. Has 'result' field: {}", rootNode.has("result"));
            log.debug("Has 'status' field: {}", rootNode.has("status"));

            if (rootNode.has("status")) {
                log.debug("Status value: {}", rootNode.get("status").asText());
            }

            if (rootNode.has("result")) {
                String result = rootNode.get("result").asText();
                log.debug("Result field length: {}", result.length());

                // Clean markdown formatting
                String cleaned = cleanMarkdownFormatting(result);
                log.debug("Cleaned result length: {}", cleaned.length());

                return cleaned;
            } else {
                log.warn("Response doesn't have 'result' field, returning raw response");
                return rawResponse;
            }
        } catch (Exception e) {
            log.error("Failed to parse JSON response", e);
            log.debug("Raw response that failed to parse: {}", rawResponse);
            return rawResponse;
        }
    }

    private String cleanMarkdownFormatting(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        return text
                // Remove bold markers
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                // Remove italic markers
                .replaceAll("\\*([^*]+)\\*", "$1")
                // Remove headers (###, ##, #)
                .replaceAll("(?m)^#{1,6}\\s+", "")
                // Remove horizontal rules (---)
                .replaceAll("(?m)^---+$", "")
                // Clean up multiple newlines
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private void saveIndictmentToCase(String caseNumber, String indictment) {
        if (indictment == null || indictment.trim().isEmpty()) {
            log.error("Attempting to save empty indictment for case: {}", caseNumber);
            throw new IllegalStateException("Cannot save empty indictment");
        }

        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        caseEntity.setIndictment(indictment);
        caseRepository.save(caseEntity);

        log.info("Indictment saved for case: {} (length: {})", caseNumber, indictment.length());
    }

    private String getIndictmentFromCase(String caseNumber) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        String indictment = caseEntity.getIndictment();

        if (indictment == null || indictment.isEmpty()) {
            throw new IllegalStateException("Indictment not found for case: " + caseNumber);
        }

        return indictment;
    }

    private String buildIndictmentUrl() {
        return UrlBuilder.buildUrl(pythonHost, pythonPort, INDICTMENT_ENDPOINT);
    }

    private Map<String, Object> buildRequestBody(String caseNumber) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("working_dir",caseNumber);
        requestBody.put("mode", "hybrid");
        return requestBody;
    }
}