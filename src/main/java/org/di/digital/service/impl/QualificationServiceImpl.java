package org.di.digital.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.enums.MessageConstant;
import org.di.digital.model.Case;
import org.di.digital.model.enums.CaseActivityType;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.repository.CaseRepository;
import org.di.digital.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
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
    private final DocumentFormatterService documentFormatterService;
    private final CaseService caseService;
    private final StreamingService streamingService;
    private final LogService logService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${qualification.model.host}")
    private String pythonHost;

    @Value("${qualification.model.port}")
    private String pythonPort;

    @Override
    public SseEmitter generateQualification(String caseNumber, String email) {
        SseEmitter emitter = new SseEmitter(0L);
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        executor.execute(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes);
            try {
                streamQualification(caseNumber, emitter, email, requestAttributes);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        return emitter;
    }

    private void streamQualification(String caseNumber, SseEmitter emitter, String userEmail, RequestAttributes requestAttributes) {

        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));
        if (!entity.isAtLeastOneFileProcessed()) {
            String message = MessageConstant.NO_FILE_PROCESSED.format(caseNumber);
            log.warn(message);
            logService.log(
                    String.format("No file processed for qualification request in case %s", caseNumber),
                    LogLevel.ERROR,
                    LogAction.NO_FILE_PROCESSED,
                    caseNumber,
                    userEmail
            );
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }
        streamingService.stream(
                qualificationUrl(pythonHost, pythonPort, caseNumber),
                List.of(),
                emitter,
                this::extractChunk,
                fullText -> {
                    RequestContextHolder.setRequestAttributes(requestAttributes);
                    try {
                        saveQualification(caseNumber, fullText);
                        caseService.updateCaseActivity(caseNumber, CaseActivityType.QUALIFICATION_GENERATED.getDescription());
                        log.info("Qualification streaming completed for case {}", caseNumber);
                        logService.log(
                                String.format("Getting case qualification by %s user in case %s", userEmail, caseNumber),
                                LogLevel.INFO,
                                LogAction.QUALIFICATION,
                                caseNumber,
                                userEmail
                        );
                    }finally {
                        RequestContextHolder.resetRequestAttributes();
                    }
                },
                error -> {
                    RequestContextHolder.setRequestAttributes(requestAttributes);
                    try {
                        log.error("Qualification streaming error for case {}", caseNumber, error);
                        if (error instanceof WebClientResponseException.BadRequest) {
                            logService.log(
                                    String.format("No required files found in case %s", caseNumber),
                                    LogLevel.ERROR,
                                    LogAction.NO_SUCH_FILE,
                                    caseNumber,
                                    userEmail
                            );
                            emitter.completeWithError(new IllegalStateException(
                                    "Квалификация деяния не может быть сгенерирована, поскольку в материалах дела " +
                                            "отсутствуют необходимые документы: ПОСТАНОВЛЕНИЕ о признании лица в качестве " +
                                            "подозреваемого либо ПРОТОКОЛ задержания лица, подозреваемого в совершении " +
                                            "уголовного правонарушения."));
                        } else {
                            emitter.completeWithError(error);
                        }
                    }
                    finally {
                            RequestContextHolder.resetRequestAttributes();
                        }
                }
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
        entity.setQualificationGeneratedAt(LocalDateTime.now());
        caseRepository.save(entity);
    }

    @Override
    public Resource downloadQualificationAsWord(String caseNumber, String userEmail) {
        try {
            logService.log(
                    String.format("Downloading qualification by %s user in case %s", userEmail, caseNumber),
                    LogLevel.INFO,
                    LogAction.QUALIFICATION_DOWNLOAD,
                    caseNumber,
                    userEmail
            );
            return new ByteArrayResource(
                    documentFormatterService.generateQualificationDocument(getQualification(caseNumber))
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