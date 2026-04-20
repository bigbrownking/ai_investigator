package org.di.digital.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.enums.MessageConstant;
import org.di.digital.model.Case;
import org.di.digital.model.User;
import org.di.digital.model.enums.CaseActivityType;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.repository.CaseFileRepository;
import org.di.digital.repository.CaseInterrogationRepository;
import org.di.digital.repository.CaseRepository;
import org.di.digital.repository.UserRepository;
import org.di.digital.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.di.digital.util.RequestBodyBuilder.indictmentBody;
import static org.di.digital.util.UrlBuilder.indictmentUrl;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndictmentServiceImpl implements IndictmentService {

    private final CaseRepository caseRepository;
    private final CaseInterrogationRepository caseInterrogationRepository;
    private final CaseFileRepository caseFileRepository;
    private final ObjectMapper mapper;
    private final WordDocumentService wordDocumentService;
    private final CaseService caseService;
    private final StreamingService streamingService;
    private final LogService logService;
    private final UserRepository userRepository;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${indictment.model.host}")
    private String pythonHost;

    @Value("${indictment.model.port}")
    private String pythonPort;

    @Override
    public SseEmitter generateIndictment(String caseNumber, String email) {
        SseEmitter emitter = new SseEmitter(0L);
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        executor.execute(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes);
            try {
                streamIndictment(caseNumber, emitter, email);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        return emitter;
    }

    @Override
    public SseEmitter completeIndictment(String caseNumber, String email) {
        SseEmitter emitter = new SseEmitter(0L);
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        executor.execute(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes);
            try {
                completeIndictment(caseNumber, emitter, email);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        return emitter;
    }

    private void streamIndictment(String caseNumber, SseEmitter emitter, String email) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (entity.getQualificationsUploaded() == null || entity.getQualificationsUploaded().isEmpty()) {
            String message = MessageConstant.NO_QUALIFICATION.format(caseNumber);
            log.warn(message);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }
        boolean isAllProcessed = !caseFileRepository.existsByCaseEntityIdAndStatusNotIn(
                entity.getId(),
                List.of(CaseFileStatusEnum.COMPLETED, CaseFileStatusEnum.FAILED)
        );
        if (!isAllProcessed) {
            String message = MessageConstant.ALL_FILES_PROCESSED.format(caseNumber);
            log.warn(message);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }
        streamingService.stream(
                indictmentUrl(pythonHost, pythonPort),
                indictmentBody(caseNumber, entity.getQualificationsUploaded(), user.getId(), false),
                emitter,
                this::extractChunk,
                fullText -> {
                    saveIndictment(caseNumber, fullText, false);
                    caseService.updateCaseActivity(caseNumber, CaseActivityType.INDICTMENT_GENERATED.name());
                    log.info("Indictment streaming completed for case {}", caseNumber);
                },
                error -> log.error("Indictment streaming error for case {}", caseNumber, error)
        );
        logService.log(
                String.format("Getting case indictment by %s user in case %s", email, caseNumber),
                LogLevel.INFO,
                LogAction.INDICTMENT,
                caseNumber,
                email
        );
    }

    private void completeIndictment(String caseNumber, SseEmitter emitter, String email) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (entity.getQualificationsUploaded() == null || entity.getQualificationsUploaded().isEmpty()) {
            String message = MessageConstant.NO_QUALIFICATION.format(caseNumber);
            log.warn(message);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }
        boolean isAllProcessed = !caseFileRepository.existsByCaseEntityIdAndStatusNotIn(
                entity.getId(),
                List.of(CaseFileStatusEnum.COMPLETED, CaseFileStatusEnum.FAILED)
        );
        if (!isAllProcessed) {
            String message = MessageConstant.ALL_FILES_PROCESSED.format(caseNumber);
            log.warn(message);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }
        boolean isAllInterrogationClosed = caseInterrogationRepository
                .countNonClosedInterrogations(entity.getId()) == 0;
        if (!isAllInterrogationClosed) {
            String message = MessageConstant.ALL_INTERROGATION_PROCESSED.format(caseNumber);
            log.warn(message);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }
        streamingService.stream(
                indictmentUrl(pythonHost, pythonPort),
                indictmentBody(caseNumber, entity.getQualificationsUploaded(), user.getId(), true),
                emitter,
                this::extractChunk,
                fullText -> {
                    saveIndictment(caseNumber, fullText, true);
                    caseService.updateCaseActivity(caseNumber, CaseActivityType.INDICTMENT_GENERATED.name());
                    log.info("Indictment streaming completed for case {}", caseNumber);
                },
                error -> log.error("Indictment streaming error for case {}", caseNumber, error)
        );
        logService.log(
                String.format("Getting case final indictment by %s user in case %s", email, caseNumber),
                LogLevel.INFO,
                LogAction.INDICTMENT_FINAL,
                caseNumber,
                email
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

    private void saveIndictment(String caseNumber, String text, boolean isDone) {
        if (text.isBlank()) return;
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));
        entity.setIndictment(text);
        entity.setIndictmentGeneratedAt(LocalDateTime.now());
        entity.setIsFinalIndictmentDone(isDone);
        caseRepository.save(entity);
    }

    @Override
    public Resource downloadIndictmentAsWord(String caseNumber, String userEmail) {
        try {
            logService.log(
                    String.format("Downloading indictment by %s user in case %s", userEmail, caseNumber),
                    LogLevel.INFO,
                    LogAction.INDICTMENT_DOWNLOAD,
                    caseNumber,
                    userEmail
            );
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