package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.cases.ChatRequest;
import org.di.digital.dto.response.cases.QueryResponse;
import org.di.digital.dto.response.chat.CaseChatHistoryResponse;
import org.di.digital.dto.response.chat.CaseChatMessageDto;
import org.di.digital.dto.response.chat.ReferenceLinkDto;
import org.di.digital.dto.response.interrogation.ContradictionDto;
import org.di.digital.dto.response.interrogation.ContradictionResponse;
import org.di.digital.dto.response.interrogation.InterrogationQuestionsResponse;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.IllegalStateMessage;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseChatMessage;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.enums.cases.MessageRole;
import org.di.digital.model.enums.log.LogAction;
import org.di.digital.model.enums.log.LogLevel;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.interrogation.CaseInterrogationCaseChat;
import org.di.digital.model.interrogation.CaseInterrogationChat;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseChatMessageRepository;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.interrogation.CaseInterrogationCaseChatRepository;
import org.di.digital.repository.interrogation.CaseInterrogationChatRepository;
import org.di.digital.repository.interrogation.CaseInterrogationContradictionRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.LogService;
import org.di.digital.service.interrogation.CaseInterrogationChatService;
import org.di.digital.util.mapper.MessageMapper;
import org.di.digital.util.requests.UserUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.di.digital.util.requests.RequestBodyBuilder.generalChatBody;
import static org.di.digital.util.requests.RequestBodyBuilder.interrogationContradictionBody;
import static org.di.digital.util.requests.RequestUrlBuilder.*;
import static org.di.digital.util.requests.UserUtil.getCurrentLang;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseInterrogationChatServiceImpl implements CaseInterrogationChatService {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseFileRepository caseFileRepository;
    private final CaseInterrogationChatRepository caseInterrogationChatRepository;
    private final CaseInterrogationCaseChatRepository caseInterrogationCaseChatRepository;
    private final CaseChatMessageRepository chatMessageRepository;
    private final LogService logService;
    private final InterrogationChatWriter interrogationChatWriter;
    private final InterrogationQuestionsWriter questionsWriter;
    private final CaseInterrogationContradictionWriter contradictionWriter;
    private final CaseInterrogationContradictionRepository contradictionRepository;
    private final WebClient.Builder webClientBuilder;
    private final MessageMapper messageMapper;
    private final UserUtil userUtil;

    @Value("${model.host}")
    private String pythonHost;

    @Value("${interrogation.port}")
    private String interrogationChatPort;

    @Value("${qualification.port}")
    private String chatPort;

    // ========================================================================
    // Генерация вопросов: prepare (tx) -> HTTP (без tx) -> saveQuestions (tx)
    // ========================================================================
    @Override
    public void streamInterrogationChatResponse(Long caseId, Long interrogationId,
                                                ChatRequest request, String userEmail,
                                                SseEmitter emitter) {
        InterrogationQuestionsWriter.PreparedInterrogation prep;
        try {
            prep = questionsWriter.prepare(
                    caseId, interrogationId, request.getQaId(),
                    request.getQuestion(), request.getAnswer(), userEmail);
        } catch (Exception e) {
            log.error("Interrogation prepare failed for interrogation {}: ", interrogationId, e);
            completeWithErrorSafely(emitter, e);
            return;
        }

        // QA уже сохранён, сообщаем фронту его id
        if (prep.qaId() != null) {
            try {
                emitter.send(SseEmitter.event().name("qa").data(Map.of("qaId", prep.qaId())));
            } catch (Exception e) {
                log.warn("Failed to send qaId {} to client for interrogation {}",
                        prep.qaId(), interrogationId, e);
            }
        }

        try {
            InterrogationQuestionsResponse response = webClientBuilder.build()
                    .post()
                    .uri(interrogationQuestionsUrl(pythonHost, interrogationChatPort, prep.caseNumber()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(prep.requestBody())
                    .retrieve()
                    .bodyToMono(InterrogationQuestionsResponse.class)
                    .block();

            if (response == null || response.getQuestions() == null) {
                throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang()));
            }

            List<String> questions = response.getQuestions();

            questionsWriter.saveQuestions(prep.chatId(), prep.placeholderId(), questions);

            emitter.send(SseEmitter.event().name("questions").data(questions));

            log.info("Saved {} question messages for interrogation {}",
                    questions.size(), interrogationId);

            logService.log(
                    String.format("New interrogation chat message %s by %s user to case %s",
                            prep.userMessageContent(), userEmail, prep.caseNumber()),
                    LogLevel.INFO, LogAction.CHAT_MESSAGE, prep.caseNumber(), userEmail);

            checkContradictions(prep, interrogationId, emitter);

            emitter.complete();

        } catch (Exception e) {
            log.error("Interrogation questions error for interrogation {}: ", interrogationId, e);
            try {
                questionsWriter.markError(prep.placeholderId(), "[Error: " + e.getMessage() + "]");
            } catch (Exception saveError) {
                log.error("Failed to mark error for placeholder {}", prep.placeholderId(), saveError);
            }
            completeWithErrorSafely(emitter, e);
        }
    }

    // ========================================================================
    // Чат по делу в контексте допроса: prepare (tx) -> HTTP (без tx) -> save (tx)
    // ========================================================================
    @Override
    public void streamCaseInterrogationChatResponse(Long caseId, Long interrogationId,
                                                    ChatRequest request, String userEmail,
                                                    SseEmitter emitter) {
        InterrogationChatWriter.PreparedCaseChat prep;
        try {
            prep = interrogationChatWriter.prepareCaseChat(
                    caseId, interrogationId, userEmail, request.getQuestion());
        } catch (Exception e) {
            log.error("Case interrogation prepare failed for interrogation {}: ", interrogationId, e);
            completeWithErrorSafely(emitter, e);
            return;
        }

        final Long messageId = prep.messageId();
        final String caseNumber = prep.caseNumber();

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        CompletableFuture.runAsync(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes);
            try {
                QueryResponse response = webClientBuilder.build()
                        .post()
                        .uri(qualificationChatUrl(pythonHost, chatPort, caseNumber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(generalChatBody(request.getQuestion()))
                        .retrieve()
                        .bodyToMono(QueryResponse.class)
                        .block();

                if (response == null || response.getResponse() == null) {
                    throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang()));
                }

                interrogationChatWriter.updateAssistantMessage(
                        messageId, response.getResponse(), response.getReferences());

                List<ReferenceLinkDto> links = (response.getReferences() == null || response.getReferences().isEmpty())
                        ? List.of()
                        : messageMapper.toLinks(response.getReferences(),
                        caseFileRepository.findAllByCaseEntityId(caseId));

                emitter.send(SseEmitter.event().name("message").data(response.getResponse()));
                emitter.send(SseEmitter.event().name("references").data(links));
                emitter.complete();

                logService.log(
                        String.format("New case interrogation chat message by %s in case %s",
                                userEmail, caseNumber),
                        LogLevel.INFO, LogAction.CHAT_MESSAGE, caseNumber, userEmail);

                log.info("Case interrogation chat completed for interrogation {}, {} references",
                        interrogationId, links.size());

            } catch (Exception e) {
                log.error("Case interrogation chat error for interrogation {}: ", interrogationId, e);
                try {
                    interrogationChatWriter.updateAssistantMessage(
                            messageId, "[Error: " + e.getMessage() + "]", null);
                } catch (Exception saveError) {
                    log.error("Failed to save error message {} for interrogation {}",
                            messageId, interrogationId, saveError);
                }
                completeWithErrorSafely(emitter, e);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
    }

    // ========================================================================
    // История и очистка
    // ========================================================================
    @Override
    @Transactional(readOnly = true)
    public CaseChatHistoryResponse getChatHistory(Long caseId, Long interrogationId,
                                                  String userEmail, int page, int size) {
        Case caseEntity = loadAndAuthorize(caseId, userEmail).caseEntity();

        CaseInterrogationChat chat = caseInterrogationChatRepository
                .findByInterrogationId(interrogationId).orElse(null);
        if (chat == null) {
            return CaseChatHistoryResponse.builder()
                    .messages(List.of())
                    .contradictions(List.of())
                    .totalMessages(0)
                    .currentPage(page)
                    .pageSize(size)
                    .build();
        }
        assertChatBelongsToCase(chat, caseEntity);

        List<CaseChatMessage> messages = chatMessageRepository
                .findByInterrogationChatIdOrderByIdAsc(chat.getId(), PageRequest.of(page, size))
                .getContent();
        long total = chatMessageRepository.countByInterrogationChatId(chat.getId());

        List<ContradictionDto> contradictions = contradictionRepository
                .findByInterrogationChatIdOrderByIdAsc(chat.getId())
                .stream()
                .map(ContradictionDto::from)
                .toList();

        return CaseChatHistoryResponse.builder()
                .chatId(chat.getId())
                .messages(mapMessages(messages, caseId))
                .contradictions(contradictions)
                .totalMessages((int) total)
                .currentPage(page)
                .pageSize(size)
                .lastMessageAt(chat.getLastMessageAt())
                .build();
    }

    @Override
    @Transactional
    public void clearChatHistory(Long caseId, Long interrogationId, String userEmail) {
        Case caseEntity = loadAndAuthorize(caseId, userEmail).caseEntity();

        String caseNumber = caseEntity.getNumber();
        CaseInterrogationChat chat = caseInterrogationChatRepository.findByInterrogationId(interrogationId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CHAT.localized(currentLang(), interrogationId.toString())));
        assertChatBelongsToCase(chat, caseEntity);

        contradictionRepository.deleteAllByInterrogationChatId(chat.getId());
        chatMessageRepository.deleteAllByInterrogationChatId(chat.getId());
        chat.getMessages().clear();
        caseInterrogationChatRepository.save(chat);

        logService.log(
                String.format("Cleared interrogation chat by %s user to case %s", userEmail, caseNumber),
                LogLevel.INFO, LogAction.CHAT_CLEAR, caseNumber, userEmail);
        log.info("Cleared chat history for interrogation {}", interrogationId);
    }

    @Override
    @Transactional
    public void toggleMessageSelected(Long caseId, Long interrogationId, Long messageId,
                                      boolean selected, String userEmail) {
        Authorized ctx = loadAndAuthorize(caseId, userEmail);
        Case caseEntity = ctx.caseEntity();

        CaseChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.MESSAGE.localized(currentLang(), messageId.toString())));

        // сообщение должно принадлежать чату именно этого допроса
        CaseInterrogationChat chat = message.getInterrogationChat();
        if (chat == null
                || chat.getInterrogation() == null
                || !Objects.equals(chat.getInterrogation().getId(), interrogationId)) {
            throw new NotFoundException(NotFoundMessage.MESSAGE.localized(currentLang(), messageId.toString()));
        }
        assertChatBelongsToCase(chat, caseEntity);

        if (Boolean.valueOf(selected).equals(message.getIsSelected())) {
            return;
        }

        if (selected && MessageRole.ASSISTANT.equals(message.getRole())) {
            List<CaseChatMessage> siblings = findGroupSiblings(chat.getId(), messageId);
            siblings.forEach(s -> s.setIsSelected(false));
            chatMessageRepository.saveAll(siblings);
        }

        message.setIsSelected(selected);
        chatMessageRepository.save(message);

        logService.log(
                String.format("Message %s %s in case %s",
                        messageId, selected ? "selected" : "deselected", caseEntity.getNumber()),
                LogLevel.INFO, LogAction.MESSAGE_SELECTED, caseEntity.getNumber(), ctx.user().getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public CaseChatHistoryResponse getCaseInterrogationChatHistory(Long caseId, Long interrogationId,
                                                                   String userEmail, int page, int size) {
        Authorized ctx = loadAndAuthorize(caseId, userEmail);

        CaseInterrogationCaseChat chat = caseInterrogationCaseChatRepository
                .findByInterrogationIdAndUserId(interrogationId, ctx.user().getId())
                .orElse(null);

        if (chat == null) {
            return CaseChatHistoryResponse.builder()
                    .messages(List.of())
                    .contradictions(List.of())
                    .totalMessages(0)
                    .currentPage(page)
                    .pageSize(size)
                    .build();
        }

        List<CaseChatMessage> messages = chatMessageRepository
                .findByCaseInterrogationCaseChatIdOrderByIdAsc(chat.getId(), PageRequest.of(page, size))
                .getContent();
        long total = chatMessageRepository.countByCaseInterrogationCaseChatId(chat.getId());

        return CaseChatHistoryResponse.builder()
                .chatId(chat.getId())
                .messages(mapMessages(messages, caseId))
                .totalMessages((int) total)
                .currentPage(page)
                .pageSize(size)
                .lastMessageAt(chat.getLastMessageAt())
                .build();
    }

    @Override
    @Transactional
    public void clearCaseInterrogationChatHistory(Long caseId, Long interrogationId, String userEmail) {
        Authorized ctx = loadAndAuthorize(caseId, userEmail);
        Case caseEntity = ctx.caseEntity();

        CaseInterrogationCaseChat chat = caseInterrogationCaseChatRepository
                .findByInterrogationIdAndUserId(interrogationId, ctx.user().getId())
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CHAT.localized(currentLang())));

        chatMessageRepository.deleteAllByCaseInterrogationCaseChatId(chat.getId());
        chat.getMessages().clear();
        caseInterrogationCaseChatRepository.save(chat);

        logService.log(
                String.format("Cleared case interrogation chat by %s in case %s", userEmail, caseEntity.getNumber()),
                LogLevel.INFO, LogAction.CHAT_CLEAR, caseEntity.getNumber(), userEmail);
    }

    // ========================================================================
    // helpers
    // ========================================================================

    private record Authorized(Case caseEntity, User user) {}

    private Authorized loadAndAuthorize(Long caseId, String userEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseId.toString())));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userEmail)));
        userUtil.validateUserAccess(caseEntity, user);
        return new Authorized(caseEntity, user);
    }

    private void assertChatBelongsToCase(CaseInterrogationChat chat, Case caseEntity) {
        if (chat.getInterrogation() == null
                || chat.getInterrogation().getCaseEntity() == null
                || !Objects.equals(chat.getInterrogation().getCaseEntity().getId(), caseEntity.getId())) {
            throw new AccessDeniedException("Chat does not belong to case " + caseEntity.getId());
        }
    }

    private List<CaseChatMessageDto> mapMessages(List<CaseChatMessage> messages, Long caseId) {
        boolean hasRefs = messages.stream()
                .anyMatch(m -> m.getReferences() != null && !m.getReferences().isEmpty());
        List<CaseFile> caseFiles = hasRefs
                ? caseFileRepository.findAllByCaseEntityId(caseId)
                : List.of();
        return messageMapper.toDtoList(messages, caseFiles);
    }

    private void checkContradictions(InterrogationQuestionsWriter.PreparedInterrogation prep,
                                     Long interrogationId, SseEmitter emitter) {
        long start = System.currentTimeMillis();
        String url = interrogationContradictionUrl(pythonHost, interrogationChatPort, prep.caseNumber());

        log.info("Contradiction check started: interrogation={}, case={}, fio={}, language={}, indication='{}'",
                interrogationId, prep.caseNumber(), prep.fio(), prep.language(), truncate(prep.answer(), 150));
        log.info("Contradiction request URL: {}", url);

        try {
            ContradictionResponse response = webClientBuilder.build()
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(interrogationContradictionBody(prep.fio(), prep.answer(), prep.language()))
                    .retrieve()
                    .bodyToMono(ContradictionResponse.class)
                    .block();

            long duration = System.currentTimeMillis() - start;

            if (response == null) {
                log.warn("Contradiction service returned null body for interrogation {} ({}ms)",
                        interrogationId, duration);
                return;
            }

            List<ContradictionResponse.ContradictionItem> items = response.getContradictions();
            if (items == null || items.isEmpty()) {
                log.info("No contradictions found for interrogation {} ({}ms)", interrogationId, duration);
                return;
            }

            log.info("Contradiction service returned {} items for interrogation {} ({}ms)",
                    items.size(), interrogationId, duration);

            List<ContradictionDto> saved = contradictionWriter
                    .save(prep.chatId(), prep.userMessageId(), prep.answer(), items)
                    .stream()
                    .map(ContradictionDto::from)
                    .toList();

            emitter.send(SseEmitter.event().name("contradictions").data(saved));

            log.info("Sent {} contradictions via SSE for interrogation {}, sourceMessageId={}",
                    saved.size(), interrogationId, prep.userMessageId());

        } catch (WebClientResponseException e) {
            log.warn("Contradiction service HTTP error for interrogation {}: {} — body: {}",
                    interrogationId, e.getStatusCode(), truncate(e.getResponseBodyAsString(), 500));
        } catch (Exception e) {
            log.warn("Contradiction check failed for interrogation {} ({}ms): {}",
                    interrogationId, System.currentTimeMillis() - start, e.getMessage(), e);
        }
    }

    private List<CaseChatMessage> findGroupSiblings(Long chatId, Long targetMessageId) {
        List<CaseChatMessage> all = chatMessageRepository
                .findByInterrogationChatIdOrderByIdAsc(chatId, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent();

        int targetIdx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(targetMessageId)) {
                targetIdx = i;
                break;
            }
        }
        if (targetIdx == -1) return List.of();

        int start = 0;
        for (int i = targetIdx - 1; i >= 0; i--) {
            if (MessageRole.USER.equals(all.get(i).getRole())) {
                start = i + 1;
                break;
            }
        }

        int end = all.size();
        for (int i = targetIdx + 1; i < all.size(); i++) {
            if (MessageRole.USER.equals(all.get(i).getRole())) {
                end = i;
                break;
            }
        }

        return all.subList(start, end).stream()
                .filter(m -> !m.getId().equals(targetMessageId))
                .filter(m -> MessageRole.ASSISTANT.equals(m.getRole()))
                .filter(m -> Boolean.TRUE.equals(m.getIsSelected()))
                .toList();
    }

    private void completeWithErrorSafely(SseEmitter emitter, Exception e) {
        try {
            emitter.completeWithError(e);
        } catch (IllegalStateException alreadyCompleted) {
            log.debug("Emitter already completed");
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private UserSettingsLanguage currentLang() {
        return getCurrentLang();
    }
}