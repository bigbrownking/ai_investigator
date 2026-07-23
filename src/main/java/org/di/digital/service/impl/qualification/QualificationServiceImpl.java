package org.di.digital.service.impl.qualification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.qualification.QualificationRephraseApplyRequest;
import org.di.digital.dto.request.qualification.QualificationSectionUpdateRequest;
import org.di.digital.dto.response.qualification.QualificationSectionDto;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.CaseActivityType;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.model.enums.MessageConstant;
import org.di.digital.model.qualification.CaseQualification;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.qualification.CaseQualificationRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.*;
import org.di.digital.service.cases.CaseService;
import org.di.digital.service.export.DocumentFormatterService;
import org.di.digital.service.impl.core.sse.SseHeartbeatUtil;
import org.di.digital.service.qualification.QualificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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

import static org.di.digital.util.requests.RequestBodyBuilder.qualificationSectionBody;
import static org.di.digital.util.requests.RequestUrlBuilder.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualificationServiceImpl implements QualificationService {

    private final CaseQualificationRepository caseQualificationRepository;
    private final CaseRepository caseRepository;
    private final ObjectMapper mapper;
    private final DocumentFormatterService documentFormatterService;
    private final CaseService caseService;
    private final LogService logService;
    private final UserRepository userRepository;
    private final WebClient.Builder webClientBuilder;
    private final QualificationWriter qualificationWriter;

    private final SseHeartbeatUtil heartbeatUtil;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${model.host}")
    private String pythonHost;

    @Value("${qualification.port}")
    private String pythonPort;

    // ---------- public API ----------

    @Override
    public SseEmitter generateQualification(String caseNumber, String email) {
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(10));
        heartbeatUtil.startHeartbeat(emitter, "qualification-" + caseNumber);

        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        executor.execute(() -> {
            RequestContextHolder.setRequestAttributes(attrs);
            try {
                streamQualification(caseNumber, emitter, email);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        return emitter;
    }

    @Override
    public SseEmitter generateQualificationSection(String caseNumber, String email, int sectionId) {
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(10));
        heartbeatUtil.startHeartbeat(emitter, "qualification-section-" + caseNumber + "-" + sectionId);

        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        executor.execute(() -> {
            RequestContextHolder.setRequestAttributes(attrs);
            try {
                streamQualificationSection(caseNumber, emitter, email, sectionId, "hybrid");
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        return emitter;
    }

    @Override
    public SseEmitter generateQualificationPrompt(String caseNumber, String email,
                                                  int startSectionId, int startOffset,
                                                  int endSectionId, int endOffset, String prompt) {
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(10));
        heartbeatUtil.startHeartbeat(emitter, "qualification-prompt-" + caseNumber);

        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        executor.execute(() -> {
            RequestContextHolder.setRequestAttributes(attrs);
            try {
                streamQualificationPrompt(caseNumber, emitter, email,
                        startSectionId, startOffset, endSectionId, endOffset, prompt);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        return emitter;
    }

    // ---------- streaming ----------

    private void streamQualification(String caseNumber, SseEmitter emitter, String email) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        if (!entity.isAtLeastOneFileProcessed()) {
            String message = MessageConstant.NO_FILE_PROCESSED.format(caseNumber);
            log.warn(message);
            logService.log(String.format("No file processed for qualification request in case %s", caseNumber),
                    LogLevel.ERROR, LogAction.NO_FILE_PROCESSED, caseNumber, email);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }

        try {
            String responseJson = webClientBuilder.build()
                    .post()
                    .uri(qualificationUrl(pythonHost, pythonPort, user.getId(), caseNumber, entity.getLanguage()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseJson == null || responseJson.isBlank()) {
                throw new IllegalStateException("Пустой ответ от сервиса");
            }

            qualificationWriter.saveQualificationRaw(caseNumber, responseJson);
            caseService.updateCaseActivity(caseNumber,
                    CaseActivityType.QUALIFICATION_GENERATED.getDescription());

            logService.log(String.format("Getting case qualification by %s user in case %s", email, caseNumber),
                    LogLevel.INFO, LogAction.QUALIFICATION, caseNumber, email);

            List<QualificationSectionDto> sections = getQualificationSections(caseNumber);
            emitter.send(SseEmitter.event().name("message").data(mapper.writeValueAsString(sections)));
            emitter.complete();

            log.info("Qualification completed for case {}", caseNumber);

        } catch (Exception e) {
            log.error("Qualification error for case {}", caseNumber, e);
            emitter.completeWithError(e);
        }
    }

    /**
     * Регенерация одной секции.
     * Сервис отвечает объектом секции: {"id":0,"text":"...","category":"header"}
     */
    private void streamQualificationSection(String caseNumber, SseEmitter emitter, String email,
                                            int sectionId, String mode) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
        String language = entity.getLanguage();

        if (entity.getQualificationSections() == null && entity.getQualification() != null) {
            emitter.completeWithError(new IllegalStateException(
                    "Ваша квалификация старого образца, сгенерируйте заново"));
            return;
        }

        if (!entity.isAtLeastOneFileProcessed()) {
            String message = MessageConstant.NO_FILE_PROCESSED.format(caseNumber);
            log.warn(message);
            logService.log(String.format("No file processed for qualification section in case %s", caseNumber),
                    LogLevel.ERROR, LogAction.NO_FILE_PROCESSED, caseNumber, email);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }

        try {
            String responseJson = webClientBuilder.build()
                    .post()
                    .uri(qualificationSectionUrl(pythonHost, pythonPort, caseNumber))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(qualificationSectionBody(sectionId, mode, language))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseJson == null || responseJson.isBlank()) {
                throw new IllegalStateException("Пустой ответ от сервиса");
            }

            qualificationWriter.saveSingleSection(caseNumber, responseJson);

            var node = mapper.readTree(responseJson);
            var sectionNode = node.has("result") ? node.get("result") : node;

            if (sectionNode == null || !sectionNode.isObject() || !sectionNode.has("id")) {
                log.error("Unexpected section response for case {}, section {}: {}",
                        caseNumber, sectionId, responseJson);
                throw new IllegalStateException("Некорректный ответ от сервиса");
            }

            log.info("Qualification section {} completed for case {}", sectionId, caseNumber);
            emitter.send(SseEmitter.event().name("message").data(sectionNode.toString()));
            emitter.complete();

        } catch (Exception e) {
            log.error("Qualification section error for case {}", caseNumber, e);
            emitter.completeWithError(e);
        }
    }

    /**
     * Перефразирование выделенного фрагмента.
     * Запрос: {"selected_text": "...", "instruction": "..."}
     * Ответ:  {"rephrased_text": "...", "status": "completed", "message": "..."}
     */
    private void streamQualificationPrompt(String caseNumber, SseEmitter emitter, String email,
                                           int startSectionId, int startOffset,
                                           int endSectionId, int endOffset, String prompt) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

        List<Map<String, Object>> sections = entity.getQualificationSections();
        if (sections == null || sections.isEmpty()) {
            emitter.completeWithError(new IllegalStateException(
                    "Секции квалификации не найдены: " + caseNumber));
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
                    .uri(qualificationPromptUrl(pythonHost, pythonPort, caseNumber))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "selected_text", context,
                            "instruction", prompt == null ? "" : prompt))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseJson == null || responseJson.isBlank()) {
                throw new IllegalStateException("Пустой ответ от сервиса");
            }

            var node = mapper.readTree(responseJson);

            var statusNode = node.get("status");
            if (statusNode != null && !"completed".equals(statusNode.asText())) {
                String msg = node.hasNonNull("message") ? node.get("message").asText() : "unknown";
                throw new IllegalStateException(
                        "Сервис вернул статус " + statusNode.asText() + ": " + msg);
            }

            var textNode = node.get("rephrased_text");
            if (textNode == null || textNode.isNull()) {
                log.error("No 'rephrased_text' in response for case {}: {}", caseNumber, responseJson);
                throw new IllegalStateException("Некорректный ответ от сервиса");
            }

            emitter.send(SseEmitter.event().name("message").data(textNode.asText()));
            emitter.complete();

        } catch (Exception e) {
            log.error("Qualification rephrase error for case {}", caseNumber, e);
            emitter.completeWithError(e);
        }
    }

    // ---------- editing ----------

    @Override
    @Transactional
    public List<QualificationSectionDto> applyRephrase(String caseNumber,
                                                       QualificationRephraseApplyRequest request) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

        if (entity.getQualificationSections() == null && entity.getQualification() != null) {
            throw new IllegalStateException("Ваша квалификация старого образца, сгенерируйте заново");
        }

        CaseQualification qualification = getOrCreateQualification(entity);
        List<Map<String, Object>> sections = new ArrayList<>(qualification.getSections());

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

        qualification.setSections(sections);
        caseQualificationRepository.save(qualification);

        log.info("Qualification rephrase applied for case {} sections {}..{}",
                caseNumber, startSectionId, endSectionId);

        return toDtoList(sections);
    }

    @Override
    @Transactional
    public QualificationSectionDto updateSection(String caseNumber,
                                                 QualificationSectionUpdateRequest request) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

        if (entity.getQualificationSections() == null && entity.getQualification() != null) {
            throw new IllegalStateException("Ваша квалификация старого образца, сгенерируйте заново");
        }

        CaseQualification qualification = getOrCreateQualification(entity);
        List<Map<String, Object>> sections = new ArrayList<>(qualification.getSections());

        Map<String, Object> target = sections.stream()
                .filter(s -> request.getId().equals(s.get("id")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Секция с id=" + request.getId() + " не найдена"));

        target.put("text", request.getText());

        qualification.setSections(sections);
        caseQualificationRepository.save(qualification);

        log.info("Qualification section {} updated for case {}", request.getId(), caseNumber);

        return QualificationSectionDto.builder()
                .id((Integer) target.get("id"))
                .category((String) target.get("category"))
                .text((String) target.get("text"))
                .build();
    }

    // ---------- read ----------

    @Override
    public List<QualificationSectionDto> getQualificationSections(String caseNumber) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

        if (entity.getQualificationSections() != null) {
            return toDtoList(entity.getQualificationSections());
        }

        if (entity.getQualification() != null) {
            return List.of(QualificationSectionDto.builder()
                    .id(0)
                    .category("legacy")
                    .text(entity.getQualification())
                    .build());
        }

        return List.of();
    }

    @Override
    public Resource downloadQualificationAsWord(String caseNumber, String userEmail) {
        try {
            logService.log(
                    String.format("Downloading qualification by %s user in case %s", userEmail, caseNumber),
                    LogLevel.INFO, LogAction.QUALIFICATION_DOWNLOAD, caseNumber, userEmail);

            Case entity = caseRepository.findByNumber(caseNumber)
                    .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

            List<Map<String, Object>> sections = entity.getQualificationSections();

            if (sections == null) {
                sections = List.of(Map.of("id", 0, "category", "legacy", "text", entity.getQualification()));
            }

            return new ByteArrayResource(
                    documentFormatterService.generateQualificationDocument(sections));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- helpers ----------

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

    private List<QualificationSectionDto> toDtoList(List<Map<String, Object>> sections) {
        return sections.stream()
                .map(s -> QualificationSectionDto.builder()
                        .id((Integer) s.get("id"))
                        .category((String) s.get("category"))
                        .text((String) s.get("text"))
                        .build())
                .toList();
    }

    private CaseQualification getOrCreateQualification(Case entity) {
        return caseQualificationRepository.findByCaseEntityNumber(entity.getNumber())
                .orElseGet(() -> CaseQualification.builder()
                        .caseEntity(entity)
                        .build());
    }
}