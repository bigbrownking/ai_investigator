package org.di.digital.service.impl.qualification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.qualification.QualificationRephraseApplyRequest;
import org.di.digital.dto.request.qualification.QualificationSectionUpdateRequest;
import org.di.digital.dto.response.qualification.QualificationSectionDto;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.IllegalStateMessage;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.cases.CaseActivityType;
import org.di.digital.model.enums.log.LogAction;
import org.di.digital.model.enums.log.LogLevel;
import org.di.digital.model.enums.MessageConstant;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.qualification.CaseQualification;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.qualification.CaseQualificationRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.*;
import org.di.digital.service.cases.CaseAccessService;
import org.di.digital.service.cases.CaseService;
import org.di.digital.service.export.DocumentFormatterService;
import org.di.digital.service.impl.core.sse.SseHeartbeatUtil;
import org.di.digital.service.qualification.QualificationService;
import org.di.digital.util.TextUtils;
import org.di.digital.util.requests.UserUtil;
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

import static org.di.digital.util.TextUtils.stripHtml;
import static org.di.digital.util.requests.RequestBodyBuilder.qualificationSectionBody;
import static org.di.digital.util.requests.RequestUrlBuilder.*;
import static org.di.digital.util.requests.UserUtil.getCurrentLang;

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
    private final CaseAccessService caseAccessService;
    private final UserUtil userUtil;

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
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));
        userUtil.validateUserAccess(entity, user);
        caseAccessService.require(entity, user, CaseModule.QUALIFICATION, CaseAction.ADD);

        if (!entity.isAtLeastOneFileProcessed()) {
            String message = MessageConstant.NO_FILE_PROCESSED.format(currentLang(), caseNumber);
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
                throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang()));
            }

            qualificationWriter.saveQualificationRaw(caseNumber, responseJson);
            caseService.updateCaseActivity(caseNumber,
                    CaseActivityType.QUALIFICATION_GENERATED.getDescription());

            logService.log(String.format("Getting case qualification by %s user in case %s", email, caseNumber),
                    LogLevel.INFO, LogAction.QUALIFICATION, caseNumber, email);

            List<QualificationSectionDto> sections = getQualificationSections(caseNumber, email);
            emitter.send(SseEmitter.event().name("message").data(mapper.writeValueAsString(sections)));
            emitter.complete();

            log.info("Qualification completed for case {}", caseNumber);

        } catch (Exception e) {
            log.error("Qualification error for case {}", caseNumber, e);
            emitter.completeWithError(e);
        }
    }

    private void streamQualificationSection(String caseNumber, SseEmitter emitter, String email,
                                            int sectionId, String mode) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));
        userUtil.validateUserAccess(entity, user);
        caseAccessService.require(entity, user, CaseModule.QUALIFICATION, CaseAction.UPDATE);

        String language = entity.getLanguage();

        if (entity.getQualificationSections() == null && entity.getQualification() != null) {
            emitter.completeWithError(new IllegalStateException(MessageConstant.OLD_QUALIFICATION.localized(currentLang())));
            return;
        }

        if (!entity.isAtLeastOneFileProcessed()) {
            String message = MessageConstant.NO_FILE_PROCESSED.format(currentLang(), caseNumber);
            log.warn(message);
            logService.log(String.format("No file processed for qualification section in case %s", caseNumber),
                    LogLevel.ERROR, LogAction.NO_FILE_PROCESSED, caseNumber, email);
            emitter.completeWithError(new IllegalStateException(message));
            return;
        }

        try {
            String responseJson = webClientBuilder.build()
                    .post()
                    .uri(qualificationSectionUrl(pythonHost, pythonPort, caseNumber, user.getId()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(qualificationSectionBody(sectionId, mode, language))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseJson == null || responseJson.isBlank()) {
                throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang()));
            }

            qualificationWriter.saveSingleSection(caseNumber, responseJson);

            var node = mapper.readTree(responseJson);
            var sectionNode = node.has("result") ? node.get("result") : node;

            if (sectionNode == null || !sectionNode.isObject() || !sectionNode.has("id")) {
                log.error("Unexpected section response for case {}, section {}: {}",
                        caseNumber, sectionId, responseJson);
                throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang()));
            }

            log.info("Qualification section {} completed for case {}", sectionId, caseNumber);
            emitter.send(SseEmitter.event().name("message").data(sectionNode.toString()));
            emitter.complete();

        } catch (Exception e) {
            log.error("Qualification section error for case {}", caseNumber, e);
            emitter.completeWithError(e);
        }
    }

    private void streamQualificationPrompt(String caseNumber, SseEmitter emitter, String email,
                                           int startSectionId, int startOffset,
                                           int endSectionId, int endOffset, String prompt) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));
        userUtil.validateUserAccess(entity, user);
        caseAccessService.require(entity, user, CaseModule.QUALIFICATION, CaseAction.UPDATE);

        List<Map<String, Object>> sections = entity.getQualificationSections();
        if (sections == null || sections.isEmpty()) {
            emitter.completeWithError(new NotFoundException(NotFoundMessage.SECTION.localized(currentLang())));
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
                throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang()));
            }

            var node = mapper.readTree(responseJson);

            var statusNode = node.get("status");
            if (statusNode != null && !"completed".equals(statusNode.asText())) {
                String msg = node.hasNonNull("message") ? node.get("message").asText() : "unknown";
                throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang()));
            }

            var textNode = node.get("rephrased_text");
            if (textNode == null || textNode.isNull()) {
                log.error("No 'rephrased_text' in response for case {}: {}", caseNumber, responseJson);
                throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang()));
            }

            emitter.send(SseEmitter.event().name("message").data(textNode.asText()));
            emitter.complete();

        } catch (Exception e) {
            log.error("Qualification rephrase error for case {}", caseNumber, e);
            emitter.completeWithError(e);
        }
    }

    @Override
    @Transactional
    public List<QualificationSectionDto> applyRephrase(String caseNumber, String email,
                                                       QualificationRephraseApplyRequest request) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));
        userUtil.validateUserAccess(entity, user);
        caseAccessService.require(entity, user, CaseModule.QUALIFICATION, CaseAction.UPDATE);


        if (entity.getQualificationSections() == null && entity.getQualification() != null) {
            throw new IllegalStateException(MessageConstant.OLD_QUALIFICATION.localized(currentLang()));
        }

        CaseQualification qualification = getOrCreateQualification(entity);
        List<Map<String, Object>> sections = new ArrayList<>(qualification.getSections());

        int startSectionId = request.getStartSectionId();
        int endSectionId   = request.getEndSectionId();
        int startOffset    = request.getStartOffset();
        int endOffset      = request.getEndOffset();
        String replacement = stripHtml(unwrapJsonString(request.getReplacementText()));

        int startIdx = indexOfSection(sections, startSectionId);
        int endIdx   = indexOfSection(sections, endSectionId);
        if (startIdx < 0) throw new NotFoundException(NotFoundMessage.SECTION.localized(currentLang(), String.valueOf(startIdx)));

        if (endIdx   < 0) throw new NotFoundException(NotFoundMessage.SECTION.localized(currentLang(), String.valueOf(endSectionId)));

        if (startIdx > endIdx) throw new IllegalStateException(IllegalStateMessage.INVALID_STATE.localized(currentLang()));

        if (startIdx == endIdx) {
            Map<String, Object> s = sections.get(startIdx);
            String raw = (String) s.get("text");

            int rawStart = TextUtils.visibleOffsetToRawOffset(raw, startOffset);
            int rawEnd   = TextUtils.visibleOffsetToRawOffset(raw, endOffset);

            checkRange(raw, rawStart, rawEnd);
            s.put("text", raw.substring(0, rawStart) + replacement + raw.substring(rawEnd));

        } else {
            Map<String, Object> startSection = sections.get(startIdx);
            Map<String, Object> endSection   = sections.get(endIdx);
            String startRaw = (String) startSection.get("text");
            String endRaw   = (String) endSection.get("text");

            int rawStart = TextUtils.visibleOffsetToRawOffset(startRaw, startOffset);
            int rawEnd   = TextUtils.visibleOffsetToRawOffset(endRaw,   endOffset);

            checkRange(startRaw, rawStart, startRaw == null ? 0 : startRaw.length());
            checkRange(endRaw,   0,        rawEnd);

            startSection.put("text", startRaw.substring(0, rawStart) + replacement);
            endSection.put("text",   endRaw.substring(rawEnd));
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
    public QualificationSectionDto updateSection(String caseNumber, String email,
                                                 QualificationSectionUpdateRequest request) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));
        userUtil.validateUserAccess(entity, user);
        caseAccessService.require(entity, user, CaseModule.QUALIFICATION, CaseAction.UPDATE);

        if (entity.getQualificationSections() == null && entity.getQualification() != null) {
            throw new IllegalStateException(MessageConstant.OLD_QUALIFICATION.localized(currentLang()));
        }

        CaseQualification qualification = getOrCreateQualification(entity);
        List<Map<String, Object>> sections = new ArrayList<>(qualification.getSections());

        Map<String, Object> target = sections.stream()
                .filter(s -> request.getId().equals(s.get("id")))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.SECTION.localized(currentLang(), String.valueOf(request.getId()))));


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


    @Override
    public List<QualificationSectionDto> getQualificationSections(String caseNumber, String email) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));
        userUtil.validateUserAccess(entity, user);
        caseAccessService.require(entity, user, CaseModule.QUALIFICATION, CaseAction.READ);

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
                    .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userEmail)));
            userUtil.validateUserAccess(entity, user);
            caseAccessService.require(entity, user, CaseModule.QUALIFICATION, CaseAction.DOWNLOAD);

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
        if (startIdx < 0) throw new NotFoundException(NotFoundMessage.SECTION.localized(currentLang(), String.valueOf(startIdx)));
        if (endIdx < 0) throw new NotFoundException(NotFoundMessage.SECTION.localized(currentLang(), String.valueOf(endSectionId)));

        if (startIdx > endIdx) throw new IllegalStateException(IllegalStateMessage.INVALID_STATE.localized(currentLang()));

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
            throw new IllegalStateException(IllegalStateMessage.INVALID_OPERATION.localized(currentLang()));
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

    private UserSettingsLanguage currentLang(){
        return getCurrentLang();
    }
}