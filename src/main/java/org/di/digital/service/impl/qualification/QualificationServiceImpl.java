package org.di.digital.service.impl.qualification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.qualification.QualificationResponse;
import org.di.digital.model.enums.MessageConstant;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.CaseActivityType;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.service.*;
import org.di.digital.service.export.DocumentFormatterService;
import org.di.digital.service.impl.core.sse.SseHeartbeatUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.di.digital.util.requests.RequestUrlBuilder.qualificationUrl;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualificationServiceImpl implements QualificationService {

    private final CaseRepository caseRepository;
    private final DocumentFormatterService documentFormatterService;
    private final CaseService caseService;
    private final WebClient.Builder webClientBuilder;
    private final QualificationWriter qualificationWriter;
    private final LogService logService;


    private final SseHeartbeatUtil heartbeatUtil;

    @Value("${model.host}")
    private String pythonHost;

    @Value("${qualification.port}")
    private String pythonPort;

    @Override
    public SseEmitter generateQualification(String caseNumber, String email) {
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(10));
        heartbeatUtil.startHeartbeat(emitter, "qualification-" + caseNumber);

        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));
        String language = entity.getLanguage();
        if (!entity.isAtLeastOneFileProcessed()) {
            String message = MessageConstant.NO_FILE_PROCESSED.format(caseNumber);
            log.warn(message);
            logService.log(
                    String.format("No file processed for qualification request in case %s", caseNumber),
                    LogLevel.ERROR,
                    LogAction.NO_FILE_PROCESSED,
                    caseNumber,
                    email
            );
            emitter.completeWithError(new IllegalStateException(message));
            return emitter;
        }

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        CompletableFuture.runAsync(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes);
            try {
                QualificationResponse response = webClientBuilder.build()
                        .post()
                        .uri(qualificationUrl(pythonHost, pythonPort, caseNumber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(QualificationResponse.class)
                        .block();

                if (response == null || response.getAnswer() == null) {
                    throw new IllegalStateException("Пустой ответ от сервиса");
                }

                emitter.send(SseEmitter.event().name("message").data(response.getAnswer()));
                emitter.complete();

                qualificationWriter.saveQualification(caseNumber, response.getAnswer());
                caseService.updateCaseActivity(caseNumber,
                        CaseActivityType.QUALIFICATION_GENERATED.getDescription());

                logService.log(
                        String.format("Getting case qualification by %s user in case %s", email, caseNumber),
                        LogLevel.INFO,
                        LogAction.QUALIFICATION,
                        caseNumber,
                        email
                );

                log.info("Qualification completed for case {}", caseNumber);

            } catch (Exception e) {
                log.error("Qualification error for case {}: ", caseNumber, e);
                emitter.completeWithError(e);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });

        return emitter;
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
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getQualification(String caseNumber) {
        return caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found"))
                .getQualification();
    }
}