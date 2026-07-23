package org.di.digital.service.impl.indictment;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.indictment.IndictmentRephraseApplyRequest;
import org.di.digital.dto.request.indictment.IndictmentSectionUpdateRequest;
import org.di.digital.dto.response.indictment.IndictmentSectionDto;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.CaseActivityType;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.model.enums.MessageConstant;
import org.di.digital.model.indictment.CaseIndictment;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.indictment.CaseIndictmentRepository;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.*;
import org.di.digital.service.cases.CaseService;
import org.di.digital.service.core.StreamingService;
import org.di.digital.service.export.DocumentFormatterService;
import org.di.digital.service.impl.core.sse.SseHeartbeatUtil;
import org.di.digital.service.indictment.IndictmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.di.digital.util.requests.RequestBodyBuilder.*;
import static org.di.digital.util.requests.RequestUrlBuilder.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndictmentServiceImpl implements IndictmentService {

    private final CaseIndictmentRepository caseIndictmentRepository;
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
    private final IndictmentWriter indictmentWriter;

    private final SseHeartbeatUtil heartbeatUtil;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${model.host}")
    private String pythonHost;

    @Value("${indictment.port}")
    private String pythonPort;

    // ---------- public API ----------

    @Override
    public SseEmitter generateIndictment(String caseNumber, String email) {
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(10));
        heartbeatUtil.startHeartbeat(emitter, "indictment-" + caseNumber);

        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        executor.execute(() -> {
            RequestContextHolder.setRequestAttributes(attrs);
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
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();

        executor.execute(() -> {
            RequestContextHolder.setRequestAttributes(attrs);
            try {
                completeIndictment(caseNumber, emitter, email, attrs);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        return emitter;
    }

    @Override
    public SseEmitter generateIndictmentSection(String caseNumber, String email, int sectionId) {
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(10));
        heartbeatUtil.startHeartbeat(emitter, "indictment-section-" + caseNumber + "-" + sectionId);

        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        executor.execute(() -> {
            RequestContextHolder.setRequestAttributes(attrs);
            try {
                streamIndictmentSection(caseNumber, emitter, email, sectionId);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        return emitter;
    }

    @Override
    public SseEmitter generateIndictmentPrompt(String caseNumber, String email,
                                               int startSectionId, int startOffset,
                                               int endSectionId, int endOffset, String prompt) {
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(10));
        heartbeatUtil.startHeartbeat(emitter, "indictment-prompt-" + caseNumber);

        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        executor.execute(() -> {
            RequestContextHolder.setRequestAttributes(attrs);
            try {
                streamIndictmentPrompt(caseNumber, emitter, email,
                        startSectionId, startOffset, endSectionId, endOffset, prompt);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        return emitter;
    }

    // ---------- streaming ----------

    private void streamIndictment(String caseNumber, SseEmitter emitter, String email) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

        String language = entity.getLanguage();

        if (entity.getQualificationsUploaded() == null || entity.getQualificationsUploaded().isEmpty()) {
            String message = MessageConstant.NO_QUALIFICATION.format(caseNumber);
            log.warn(message);
            logService.log(String.format("No qualification file in case %s", caseNumber),
                    LogLevel.ERROR, LogAction.NO_QUALIFICATION, caseNumber, email);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }

        if (!isAllFilesProcessed(entity)) {
            String message = MessageConstant.ALL_FILES_PROCESSED.format(caseNumber);
            log.warn(message);
            logService.log(String.format("No file processed for indictment request in case %s", caseNumber),
                    LogLevel.ERROR, LogAction.NO_FILE_PROCESSED, caseNumber, email);
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

            if (responseJson == null || responseJson.isBlank()) {
                throw new IllegalStateException("Пустой ответ от сервиса");
            }

            indictmentWriter.saveIndictmentRaw(caseNumber, responseJson, false);
            caseService.updateCaseActivity(caseNumber,
                    CaseActivityType.INDICTMENT_GENERATED.getDescription());

            logService.log(String.format("Getting case indictment by %s user in case %s", email, caseNumber),
                    LogLevel.INFO, LogAction.INDICTMENT, caseNumber, email);

            List<IndictmentSectionDto> sections = getIndictmentSections(caseNumber);
            emitter.send(SseEmitter.event().name("message").data(mapper.writeValueAsString(sections)));
            emitter.complete();

            log.info("Indictment completed for case {}", caseNumber);

        } catch (Exception e) {
            log.error("Indictment request error for case {}", caseNumber, e);
            emitter.completeWithError(e);
        }
    }

    private void completeIndictment(String caseNumber, SseEmitter emitter,
                                    String email, RequestAttributes attrs) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

        String language = entity.getLanguage();

        if (entity.getQualificationsUploaded() == null || entity.getQualificationsUploaded().isEmpty()) {
            String message = MessageConstant.NO_QUALIFICATION.format(caseNumber);
            log.warn(message);
            logService.log(String.format("No qualification file in case %s", caseNumber),
                    LogLevel.ERROR, LogAction.NO_QUALIFICATION, caseNumber, email);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }

        if (!isAllFilesProcessed(entity)) {
            String message = MessageConstant.ALL_FILES_PROCESSED.format(caseNumber);
            log.warn(message);
            logService.log(String.format("No file processed for indictment request in case %s", caseNumber),
                    LogLevel.ERROR, LogAction.NO_FILE_PROCESSED, caseNumber, email);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }

        boolean isAllInterrogationClosed =
                caseInterrogationRepository.countNonClosedInterrogations(entity.getId()) == 0;
        if (!isAllInterrogationClosed) {
            String message = MessageConstant.ALL_INTERROGATION_PROCESSED.format(caseNumber);
            log.warn(message);
            logService.log(String.format("No interrogations closed for indictment request in case %s", caseNumber),
                    LogLevel.ERROR, LogAction.NO_INTERROGATION_CLOSED, caseNumber, email);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }

        streamingService.stream(
                indictmentUrl(pythonHost, pythonPort),
                indictmentBody(caseNumber, entity.getQualificationsUploaded(), user.getId(), true, language),
                emitter,
                this::extractChunk,
                fullText -> {
                    RequestContextHolder.setRequestAttributes(attrs);
                    try {
                        indictmentWriter.saveIndictmentRaw(caseNumber, fullText, true);
                        caseService.updateCaseActivity(caseNumber,
                                CaseActivityType.INDICTMENT_GENERATED.getDescription());
                        log.info("Indictment streaming completed for case {}", caseNumber);
                        logService.log(
                                String.format("Getting case final indictment by %s user in case %s", email, caseNumber),
                                LogLevel.INFO, LogAction.INDICTMENT_FINAL, caseNumber, email);
                    } finally {
                        RequestContextHolder.resetRequestAttributes();
                    }
                },
                error -> log.error("Indictment streaming error for case {}", caseNumber, error)
        );
    }

    private void streamIndictmentSection(String caseNumber, SseEmitter emitter,
                                         String email, int sectionId) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

        if (entity.getIndictmentSections() == null && entity.getIndictment() != null) {
            emitter.completeWithError(new IllegalStateException(
                    "Ваш обвинительный акт старого образца, сгенерируйте заново"));
            return;
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

        if (entity.getQualificationsUploaded() == null || entity.getQualificationsUploaded().isEmpty()) {
            String message = MessageConstant.NO_QUALIFICATION.format(caseNumber);
            log.warn(message);
            logService.log(String.format("No qualification file in case %s", caseNumber),
                    LogLevel.ERROR, LogAction.NO_QUALIFICATION, caseNumber, email);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }

        try {
            String responseJson = webClientBuilder.build()
                    .post()
                    .uri(indictmentSectionUrl(pythonHost, pythonPort))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(indictmentSectionBody(caseNumber, entity.getQualificationsUploaded(),
                            user.getId(), false, entity.getLanguage(), sectionId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseJson == null || responseJson.isBlank()) {
                throw new IllegalStateException("Пустой ответ от сервиса");
            }

            indictmentWriter.saveSingleSection(caseNumber, responseJson);
            log.info("Indictment section {} completed for case {}", sectionId, caseNumber);

            var node = mapper.readTree(responseJson);
            emitter.send(SseEmitter.event().name("message").data(node.get("result").toString()));
            emitter.complete();

        } catch (Exception e) {
            log.error("Indictment section request error for case {}", caseNumber, e);
            emitter.completeWithError(e);
        }
    }

    private void streamIndictmentPrompt(String caseNumber, SseEmitter emitter, String email,
                                        int startSectionId, int startOffset,
                                        int endSectionId, int endOffset, String prompt) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

        if (entity.getQualificationsUploaded() == null || entity.getQualificationsUploaded().isEmpty()) {
            String message = MessageConstant.NO_QUALIFICATION.format(caseNumber);
            log.warn(message);
            logService.log(String.format("No qualification file in case %s", caseNumber),
                    LogLevel.ERROR, LogAction.NO_QUALIFICATION, caseNumber, email);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }

        List<Map<String, Object>> sections = entity.getIndictmentSections();
        if (sections == null || sections.isEmpty()) {
            emitter.completeWithError(new IllegalStateException("Секции акта не найдены: " + caseNumber));
            return;
        }

        String context;
        try {
            context = extractContext(sections, startSectionId, startOffset, endSectionId, endOffset);
        } catch (IllegalStateException e) {
            emitter.completeWithError(e);
            return;
        }

        try {
            String responseJson = webClientBuilder.build()
                    .post()
                    .uri(indictmentPromptUrl(pythonHost, pythonPort))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(indictmentPromptBody(caseNumber, context, prompt, entity.getLanguage()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseJson == null || responseJson.isBlank()) {
                throw new IllegalStateException("Пустой ответ от сервиса");
            }

            var node = mapper.readTree(responseJson);
            emitter.send(SseEmitter.event().name("message").data(node.get("result").asText()));
            emitter.complete();

        } catch (Exception e) {
            log.error("Indictment rephrase error for case {}", caseNumber, e);
            emitter.completeWithError(e);
        }
    }

    private String extractChunk(String chunk) {
        try {
            var node = mapper.readTree(chunk);
            if (node.has("delta")) return node.get("delta").asText();
            if (node.has("result")) return node.get("result").asText();
        } catch (Exception ignored) {
        }
        return chunk;
    }

    // ---------- editing ----------

    @Override
    @Transactional
    public List<IndictmentSectionDto> applyRephrase(String caseNumber,
                                                    IndictmentRephraseApplyRequest request) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

        if (entity.getIndictmentSections() == null && entity.getIndictment() != null) {
            throw new IllegalStateException("Ваш обвинительный акт старого образца, сгенерируйте заново");
        }
        if (entity.getIndictmentSections() == null) {
            throw new IllegalStateException("Обвинительный акт не найден для дела: " + caseNumber);
        }

        CaseIndictment indictment = getOrCreateIndictment(entity);
        List<Map<String, Object>> sections = new ArrayList<>(indictment.getSections());

        int startSectionId = request.getStartSectionId();
        int endSectionId = request.getEndSectionId();
        int startOffset = request.getStartOffset();
        int endOffset = request.getEndOffset();
        String replacement = unwrapJsonString(request.getReplacementText());

        int startIdx = indexOfSection(sections, startSectionId);
        int endIdx = indexOfSection(sections, endSectionId);
        if (startIdx < 0) throw new IllegalStateException("Секция id=" + startSectionId + " не найдена");
        if (endIdx < 0) throw new IllegalStateException("Секция id=" + endSectionId + " не найдена");
        if (startIdx > endIdx) throw new IllegalStateException(
                "Начальная секция идёт позже конечной: start=" + startSectionId + ", end=" + endSectionId);

        if (startIdx == endIdx) {
            Map<String, Object> s = sections.get(startIdx);
            String text = (String) s.get("text");
            checkRange(text, startOffset, endOffset);
            s.put("text", text.substring(0, startOffset) + replacement + text.substring(endOffset));
        } else {
            Map<String, Object> startSection = sections.get(startIdx);
            Map<String, Object> endSection = sections.get(endIdx);
            String startText = (String) startSection.get("text");
            String endText = (String) endSection.get("text");
            checkRange(startText, startOffset, startText == null ? 0 : startText.length());
            checkRange(endText, 0, endOffset);

            startSection.put("text", startText.substring(0, startOffset) + replacement);
            endSection.put("text", endText.substring(endOffset));
            for (int i = startIdx + 1; i < endIdx; i++) {
                sections.get(i).put("text", "");
            }
        }

        indictment.setSections(sections);
        caseIndictmentRepository.save(indictment);

        log.info("Indictment rephrase applied for case {} sections {}..{}",
                caseNumber, startSectionId, endSectionId);

        return toDtoList(sections);
    }

    @Override
    @Transactional
    public IndictmentSectionDto updateSection(String caseNumber,
                                              IndictmentSectionUpdateRequest request) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

        if (entity.getIndictmentSections() == null && entity.getIndictment() != null) {
            throw new IllegalStateException("Ваш обвинительный акт старого образца, сгенерируйте заново");
        }
        if (entity.getIndictmentSections() == null) {
            throw new IllegalStateException("Обвинительный акт не найден для дела: " + caseNumber);
        }

        CaseIndictment indictment = getOrCreateIndictment(entity);
        List<Map<String, Object>> sections = new ArrayList<>(indictment.getSections());

        Map<String, Object> target = sections.stream()
                .filter(s -> request.getId().equals(s.get("id")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Секция с id=" + request.getId() + " не найдена"));

        target.put("text", request.getText());

        indictment.setSections(sections);
        caseIndictmentRepository.save(indictment);

        log.info("Indictment section {} updated for case {}", request.getId(), caseNumber);

        return IndictmentSectionDto.builder()
                .id((Integer) target.get("id"))
                .category((String) target.get("category"))
                .text((String) target.get("text"))
                .build();
    }

    // ---------- read ----------

    @Override
    public List<IndictmentSectionDto> getIndictmentSections(String caseNumber) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

        if (entity.getIndictmentSections() != null) {
            return toDtoList(entity.getIndictmentSections());
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
    public Resource downloadIndictmentAsWord(String caseNumber, String userEmail) {
        try {
            logService.log(
                    String.format("Downloading indictment by %s user in case %s", userEmail, caseNumber),
                    LogLevel.INFO, LogAction.INDICTMENT_DOWNLOAD, caseNumber, userEmail);

            Case entity = caseRepository.findByNumber(caseNumber)
                    .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

            List<Map<String, Object>> sections = entity.getIndictmentSections();

            if (sections == null) {
                if (entity.getIndictment() == null) {
                    throw new IllegalStateException("Обвинительный акт не найден для дела: " + caseNumber);
                }
                sections = List.of(Map.of("id", 0, "category", "legacy", "text", entity.getIndictment()));
            }

            return new ByteArrayResource(
                    documentFormatterService.generateIndictmentDocument(sections));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- helpers ----------

    private boolean isAllFilesProcessed(Case entity) {
        return !caseFileRepository.existsByCaseEntityIdAndStatusNotIn(
                entity.getId(), List.of(CaseFileStatusEnum.COMPLETED, CaseFileStatusEnum.FAILED));
    }

    private String unwrapJsonString(String raw) {
        String value = raw == null ? "" : raw;
        String probe = value.strip();
        if (probe.length() >= 2 && probe.startsWith("\"") && probe.endsWith("\"")) {
            try {
                return mapper.readValue(probe, String.class);
            } catch (Exception ignored) {
                // оставляем как есть
            }
        }
        return value;
    }

    private String extractContext(List<Map<String, Object>> sections,
                                  int startSectionId, int startOffset,
                                  int endSectionId, int endOffset) {
        int startIdx = indexOfSection(sections, startSectionId);
        int endIdx = indexOfSection(sections, endSectionId);
        if (startIdx < 0) throw new IllegalStateException("Секция id=" + startSectionId + " не найдена");
        if (endIdx < 0) throw new IllegalStateException("Секция id=" + endSectionId + " не найдена");
        if (startIdx > endIdx) throw new IllegalStateException(
                "Начальная секция идёт позже конечной: start=" + startSectionId + ", end=" + endSectionId);

        if (startIdx == endIdx) {
            String text = (String) sections.get(startIdx).get("text");
            checkRange(text, startOffset, endOffset);
            return text.substring(startOffset, endOffset);
        }

        String startText = (String) sections.get(startIdx).get("text");
        String endText = (String) sections.get(endIdx).get("text");
        checkRange(startText, startOffset, startText == null ? 0 : startText.length());
        checkRange(endText, 0, endOffset);

        StringBuilder sb = new StringBuilder(startText.substring(startOffset));
        for (int i = startIdx + 1; i < endIdx; i++) {
            String mid = (String) sections.get(i).get("text");
            if (mid != null) sb.append('\n').append(mid);
        }
        sb.append('\n').append(endText, 0, endOffset);
        return sb.toString();
    }

    private void checkRange(String text, int start, int end) {
        if (text == null || start < 0 || end > text.length() || start > end) {
            throw new IllegalStateException("Некорректные позиции: start=" + start
                    + ", end=" + end + ", length=" + (text == null ? "null" : text.length()));
        }
    }

    private int indexOfSection(List<Map<String, Object>> sections, int sectionId) {
        for (int i = 0; i < sections.size(); i++) {
            if (Integer.valueOf(sectionId).equals(sections.get(i).get("id"))) return i;
        }
        return -1;
    }

    private List<IndictmentSectionDto> toDtoList(List<Map<String, Object>> sections) {
        return sections.stream()
                .map(s -> IndictmentSectionDto.builder()
                        .id((Integer) s.get("id"))
                        .category((String) s.get("category"))
                        .text((String) s.get("text"))
                        .build())
                .toList();
    }

    private CaseIndictment getOrCreateIndictment(Case entity) {
        return caseIndictmentRepository.findByCaseEntityNumber(entity.getNumber())
                .orElseGet(() -> CaseIndictment.builder()
                        .caseEntity(entity)
                        .build());
    }
}