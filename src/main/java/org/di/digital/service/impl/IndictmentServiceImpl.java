package org.di.digital.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.indictment.IndictmentSectionUpdateRequest;
import org.di.digital.dto.response.indictment.IndictmentSectionDto;
import org.di.digital.model.enums.MessageConstant;
import org.di.digital.model.cases.Case;
import org.di.digital.model.user.User;
import org.di.digital.model.enums.CaseActivityType;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.*;
import org.di.digital.service.export.DocumentFormatterService;
import org.di.digital.service.impl.core.SseHeartbeatUtil;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.di.digital.util.requests.RequestBodyBuilder.indictmentBody;
import static org.di.digital.util.requests.RequestBodyBuilder.indictmentSectionBody;
import static org.di.digital.util.requests.RequestUrlBuilder.indictmentSectionUrl;
import static org.di.digital.util.requests.RequestUrlBuilder.indictmentUrl;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndictmentServiceImpl implements IndictmentService {

    private final CaseRepository caseRepository;
    private final CaseInterrogationRepository caseInterrogationRepository;
    private final CaseFileRepository caseFileRepository;
    private final ObjectMapper mapper;
    private final DocumentFormatterService documentFormatterService;
    private final CaseService caseService;
    private final StreamingService streamingService;
    private final LogService logService;
    private final UserRepository userRepository;
    private final WebClient.Builder webClientBuilder;

    private final SseHeartbeatUtil heartbeatUtil;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${model.host}")
    private String pythonHost;

    @Value("${indictment.port}")
    private String pythonPort;

    @Override
    public SseEmitter generateIndictment(String caseNumber, String language, String email) {
        SseEmitter emitter = new SseEmitter(0L);
        heartbeatUtil.startHeartbeat(emitter);

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        executor.execute(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes);
            try {
                streamIndictment(caseNumber, emitter, language, email, requestAttributes);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        return emitter;
    }

    @Override
    public SseEmitter completeIndictment(String caseNumber, String language, String email) {
        SseEmitter emitter = new SseEmitter(0L);
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        executor.execute(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes);
            try {
                completeIndictment(caseNumber, emitter, language, email, requestAttributes);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        return emitter;
    }

    @Override
    public SseEmitter generateIndictmentSection(String caseNumber, String language, String email, int sectionId) {
        SseEmitter emitter = new SseEmitter(0L);
        heartbeatUtil.startHeartbeat(emitter);

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        executor.execute(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes);
            try {
                streamIndictmentSection(caseNumber, emitter, language, email, sectionId, requestAttributes);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        return emitter;
    }

    private void streamIndictment(String caseNumber, SseEmitter emitter, String language,
                                  String email, RequestAttributes requestAttributes) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

        if (entity.getQualificationsUploaded() == null || entity.getQualificationsUploaded().isEmpty()) {
            String message = MessageConstant.NO_QUALIFICATION.format(caseNumber);
            log.warn(message);
            logService.log(String.format("No qualification file in case %s", caseNumber),
                    LogLevel.ERROR, LogAction.NO_QUALIFICATION, caseNumber, user.getEmail());
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }

        boolean isAllProcessed = !caseFileRepository.existsByCaseEntityIdAndStatusNotIn(
                entity.getId(), List.of(CaseFileStatusEnum.COMPLETED, CaseFileStatusEnum.FAILED));
        if (!isAllProcessed) {
            String message = MessageConstant.ALL_FILES_PROCESSED.format(caseNumber);
            log.warn(message);
            logService.log(String.format("No file processed for indictment request in case %s", caseNumber),
                    LogLevel.ERROR, LogAction.NO_FILE_PROCESSED, caseNumber, user.getEmail());
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }

        try {
            String responseJson = webClientBuilder.build()
                    .post()
                    .uri(indictmentUrl(pythonHost, pythonPort))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(indictmentBody(caseNumber, entity.getQualificationsUploaded(),
                            user.getId(), false, language))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            saveIndictment(caseNumber, responseJson, false);
            caseService.updateCaseActivity(caseNumber, CaseActivityType.INDICTMENT_GENERATED.getDescription());

            logService.log(String.format("Getting case indictment by %s user in case %s", email, caseNumber),
                    LogLevel.INFO, LogAction.INDICTMENT, caseNumber, email);

            List<IndictmentSectionDto> sections = getIndictmentSections(caseNumber);
            emitter.send(SseEmitter.event().data(mapper.writeValueAsString(sections)));
            emitter.complete();

        } catch (Exception e) {
            log.error("Indictment request error for case {}", caseNumber, e);
            emitter.completeWithError(e);
        }
    }

    private void completeIndictment(String caseNumber, SseEmitter emitter, String language, String email, RequestAttributes requestAttributes) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

        if (entity.getQualificationsUploaded() == null || entity.getQualificationsUploaded().isEmpty()) {
            String message = MessageConstant.NO_QUALIFICATION.format(caseNumber);
            log.warn(message);
            logService.log(
                    String.format("No qualification file in case %s", caseNumber),
                    LogLevel.ERROR,
                    LogAction.NO_QUALIFICATION,
                    caseNumber,
                    user.getEmail()
            );
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
            logService.log(
                    String.format("No file processed for indictment request in case %s", caseNumber),
                    LogLevel.ERROR,
                    LogAction.NO_FILE_PROCESSED,
                    caseNumber,
                    user.getEmail()
            );
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }
        boolean isAllInterrogationClosed = caseInterrogationRepository
                .countNonClosedInterrogations(entity.getId()) == 0;
        if (!isAllInterrogationClosed) {
            String message = MessageConstant.ALL_INTERROGATION_PROCESSED.format(caseNumber);
            log.warn(message);
            logService.log(
                    String.format("No interrogations closed for indictment request in case %s", caseNumber),
                    LogLevel.ERROR,
                    LogAction.NO_INTERROGATION_CLOSED,
                    caseNumber,
                    user.getEmail()
            );
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }
        streamingService.stream(
                indictmentUrl(pythonHost, pythonPort),
                indictmentBody(caseNumber, entity.getQualificationsUploaded(), user.getId(), true, language),
                emitter,
                this::extractChunk,
                fullText -> {
                    RequestContextHolder.setRequestAttributes(requestAttributes);
                    try {
                        saveIndictment(caseNumber, fullText, true);
                        caseService.updateCaseActivity(caseNumber, CaseActivityType.INDICTMENT_GENERATED.getDescription());
                        log.info("Indictment streaming completed for case {}", caseNumber);
                        logService.log(
                                String.format("Getting case final indictment by %s user in case %s", email, caseNumber),
                                LogLevel.INFO,
                                LogAction.INDICTMENT_FINAL,
                                caseNumber,
                                email
                        );
                    }finally {
                        RequestContextHolder.resetRequestAttributes();
                    }
                },
                error -> log.error("Indictment streaming error for case {}", caseNumber, error)
        );
    }
    private void streamIndictmentSection(String caseNumber, SseEmitter emitter, String language,
                                         String email, int sectionId, RequestAttributes requestAttributes) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        if (entity.getIndictmentSections() == null && entity.getIndictment() != null) {
            emitter.completeWithError(new IllegalStateException(
                    "Ваш обвинительный акт старого образца, сгенерируйте заново"));
            return;
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

        if (entity.getQualificationsUploaded() == null || entity.getQualificationsUploaded().isEmpty()) {
            emitter.completeWithError(new IllegalStateException(
                    MessageConstant.NO_QUALIFICATION.format(caseNumber)));
            return;
        }

        try {
            String responseJson = webClientBuilder.build()
                    .post()
                    .uri(indictmentSectionUrl(pythonHost, pythonPort))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(indictmentSectionBody(caseNumber, entity.getQualificationsUploaded(),
                            user.getId(), false, language, sectionId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            saveSingleSection(caseNumber, responseJson);
            log.info("Indictment section {} completed for case {}", sectionId, caseNumber);

            var node = mapper.readTree(responseJson);
            emitter.send(SseEmitter.event().data(node.get("result").toString()));
            emitter.complete();

        } catch (Exception e) {
            log.error("Indictment section request error for case {}", caseNumber, e);
            emitter.completeWithError(e);
        }
    }

    private String extractChunk(String chunk) {
        try {
            var node = mapper.readTree(chunk);
            if (node.has("delta"))  return node.get("delta").asText();
            if (node.has("result")) return node.get("result").asText();
        } catch (Exception ignored) {}
        return chunk;
    }

    private void saveIndictment(String caseNumber, String rawJson, boolean isDone) {
        if (rawJson == null || rawJson.isBlank()) return;

        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        try {
            var node = mapper.readTree(rawJson);
            if (node.has("result") && node.get("result").isArray()) {
                List<Map<String, Object>> sections = mapper.convertValue(
                        node.get("result"),
                        mapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                );
                entity.setIndictmentSections(sections);
                entity.setIndictment(null);
            } else {
                entity.setIndictment(rawJson);
            }
        } catch (Exception e) {
            log.warn("Could not parse indictment as JSON, saving as plain text for case {}", caseNumber);
            entity.setIndictment(rawJson);
        }

        entity.setIndictmentGeneratedAt(LocalDateTime.now());
        entity.setIsFinalIndictmentDone(isDone);
        caseRepository.save(entity);
    }

    private void saveSingleSection(String caseNumber, String rawJson) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        try {
            var node = mapper.readTree(rawJson);
            if (!node.has("result")) return;

            Map<String, Object> newSection = mapper.convertValue(node.get("result"), Map.class);
            Integer sectionId = (Integer) newSection.get("id");

            List<Map<String, Object>> sections = entity.getIndictmentSections() != null
                    ? new ArrayList<>(entity.getIndictmentSections())
                    : new ArrayList<>();

            sections.removeIf(s -> sectionId.equals(s.get("id")));
            sections.add(newSection);
            sections.sort(Comparator.comparingInt(s -> (Integer) s.get("id")));

            entity.setIndictmentSections(sections);
            caseRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to parse indictment section response for case {}", caseNumber, e);
        }
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
                    documentFormatterService.generateIndictmentDocument(getIndictment(caseNumber))
            );
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getIndictment(String caseNumber) {
        return caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber))
                .getIndictment();
    }

    @Override
    public List<IndictmentSectionDto> getIndictmentSections(String caseNumber) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

        if (entity.getIndictmentSections() != null) {
            return entity.getIndictmentSections().stream()
                    .map(s -> IndictmentSectionDto.builder()
                            .id((Integer) s.get("id"))
                            .category((String) s.get("category"))
                            .text((String) s.get("text"))
                            .build())
                    .toList();
        }

        if (entity.getIndictment() != null) {
            return List.of(IndictmentSectionDto.builder()
                    .id(0)
                    .category("legacy")
                    .text(entity.getIndictment())
                    .build());
        }

        return List.of();
    }

    @Override
    public IndictmentSectionDto updateSection(String caseNumber, IndictmentSectionUpdateRequest request) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

        if (entity.getIndictmentSections() == null && entity.getIndictment() != null) {
            throw new IllegalStateException("Ваш обвинительный акт старого образца, сгенерируйте заново");
        }

        if (entity.getIndictmentSections() == null) {
            throw new IllegalStateException("Обвинительный акт не найден для дела: " + caseNumber);
        }

        List<Map<String, Object>> sections = new ArrayList<>(entity.getIndictmentSections());

        Map<String, Object> target = sections.stream()
                .filter(s -> request.getId().equals(s.get("id")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Секция с id=" + request.getId() + " не найдена"));

        target.put("text", request.getText());

        entity.setIndictmentSections(sections);
        caseRepository.save(entity);

        log.info("Indictment section {} updated for case {}", request.getId(), caseNumber);

        return IndictmentSectionDto.builder()
                .id((Integer) target.get("id"))
                .category((String) target.get("category"))
                .text((String) target.get("text"))
                .build();
    }
}