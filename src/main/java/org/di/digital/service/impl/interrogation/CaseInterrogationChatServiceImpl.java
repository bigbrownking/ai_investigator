package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.cases.ChatRequest;
import org.di.digital.dto.response.cases.QueryResponse;
import org.di.digital.dto.response.chat.CaseChatHistoryResponse;
import org.di.digital.dto.response.chat.CaseChatMessageDto;
import org.di.digital.dto.response.interrogation.ContradictionDto;
import org.di.digital.dto.response.interrogation.ContradictionResponse;
import org.di.digital.dto.response.interrogation.InterrogationQuestionsResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseChatMessage;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.model.enums.MessageRole;
import org.di.digital.model.interrogation.CaseInterrogationCaseChat;
import org.di.digital.model.interrogation.CaseInterrogationChat;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseChatMessageRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.interrogation.CaseInterrogationCaseChatRepository;
import org.di.digital.repository.interrogation.CaseInterrogationChatRepository;
import org.di.digital.repository.interrogation.CaseInterrogationContradictionRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.LogService;
import org.di.digital.service.interrogation.CaseInterrogationChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.di.digital.util.requests.RequestBodyBuilder.generalChatBody;
import static org.di.digital.util.requests.RequestBodyBuilder.interrogationContradictionBody;
import static org.di.digital.util.requests.RequestUrlBuilder.*;
import static org.di.digital.util.requests.UserUtil.validateUserAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseInterrogationChatServiceImpl implements CaseInterrogationChatService {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseInterrogationChatRepository caseInterrogationChatRepository;
    private final CaseInterrogationCaseChatRepository caseInterrogationCaseChatRepository;
    private final CaseChatMessageRepository chatMessageRepository;

    private final LogService logService;
    private final InterrogationChatWriter interrogationChatWriter;
    private final InterrogationQuestionsWriter questionsWriter;
    private final CaseInterrogationContradictionWriter contradictionWriter;
    private final CaseInterrogationContradictionRepository contradictionRepository;
    private final WebClient.Builder webClientBuilder;

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
                    caseId, interrogationId, request.getQuestion(), request.getAnswer(), userEmail);
        } catch (Exception e) {
            log.error("Interrogation prepare failed for interrogation {}: ", interrogationId, e);
            emitter.completeWithError(e);
            return;
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
                throw new IllegalStateException("Пустой ответ от сервиса");
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
            questionsWriter.markError(prep.placeholderId(), "[Error: " + e.getMessage() + "]");
            emitter.completeWithError(e);
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
            emitter.completeWithError(e);
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
                    throw new IllegalStateException("Пустой ответ от сервиса");
                }

                emitter.send(SseEmitter.event().name("message").data(response.getResponse()));
                emitter.complete();

                interrogationChatWriter.updateAssistantMessage(
                        messageId, response.getResponse(), response.getReferences());

                logService.log(
                        String.format("New case interrogation chat message by %s in case %s",
                                userEmail, caseNumber),
                        LogLevel.INFO, LogAction.CHAT_MESSAGE, caseNumber, userEmail);

                log.info("Case interrogation chat completed for interrogation {}", interrogationId);

            } catch (Exception e) {
                log.error("Case interrogation chat error for interrogation {}: ", interrogationId, e);
                interrogationChatWriter.updateAssistantMessage(
                        messageId, "[Error: " + e.getMessage() + "]", null);
                emitter.completeWithError(e);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
    }

    // ========================================================================
    // История и очистка — чистая работа с БД, короткие транзакции. Оставлено как есть.
    // ========================================================================
    @Override
    @Transactional(readOnly = true)
    public CaseChatHistoryResponse getChatHistory(Long caseId, Long interrogationId,
                                                  String userEmail, int page, int size) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        CaseInterrogationChat chat = caseInterrogationChatRepository
                .findByInterrogationId(interrogationId).orElse(null);
        if (chat == null) {
            return CaseChatHistoryResponse.builder()
                    .messages(List.of())
                    .contradictions(List.of())
                    .totalMessages(0)
                    .build();
        }

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
                .messages(messages.stream().map(CaseChatMessageDto::from).toList())
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
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        String caseNumber = caseEntity.getNumber();
        CaseInterrogationChat chat = caseInterrogationChatRepository.findByInterrogationId(interrogationId)
                .orElseThrow(() -> new IllegalStateException("Чат для допроса не найден: " + interrogationId));

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
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        CaseChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("Сообщение не найдено: " + messageId));

        if (Boolean.valueOf(selected).equals(message.getIsSelected())) {
            return;
        }

        if (selected && MessageRole.ASSISTANT.equals(message.getRole())) {
            CaseInterrogationChat chat = message.getInterrogationChat();
            if (chat != null) {
                List<CaseChatMessage> siblings = findGroupSiblings(chat.getId(), messageId);
                siblings.forEach(s -> s.setIsSelected(false));
                chatMessageRepository.saveAll(siblings);
            }
        }

        message.setIsSelected(selected);
        chatMessageRepository.save(message);

        logService.log(
                String.format("Message %s %s in case %s",
                        messageId, selected ? "selected" : "deselected", caseEntity.getNumber()),
                LogLevel.INFO, LogAction.MESSAGE_SELECTED, caseEntity.getNumber(), user.getEmail());
    }
    private void checkContradictions(InterrogationQuestionsWriter.PreparedInterrogation prep,
                                     Long interrogationId, SseEmitter emitter) {
        try {
            ContradictionResponse response = webClientBuilder.build()
                    .post()
                    .uri(interrogationContradictionUrl(pythonHost, interrogationChatPort, prep.caseNumber()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(interrogationContradictionBody(prep.answer(), prep.language()))
                    .retrieve()
                    .bodyToMono(ContradictionResponse.class)
                    .block();

            List<ContradictionResponse.ContradictionItem> items =
                    response == null ? null : response.getContradictions();

            if (items == null || items.isEmpty()) {
                log.debug("No contradictions for interrogation {}", interrogationId);
                return;
            }

            List<ContradictionDto> saved = contradictionWriter
                    .save(prep.chatId(), prep.userMessageId(), prep.answer(), items)
                    .stream()
                    .map(ContradictionDto::from)
                    .toList();

            emitter.send(SseEmitter.event().name("contradictions").data(saved));

            log.info("Found {} contradictions for interrogation {}", saved.size(), interrogationId);

        } catch (Exception e) {
            log.warn("Contradiction check failed for interrogation {}: {}",
                    interrogationId, e.getMessage());
        }
    }

    // NOTE: грузит ВСЕ сообщения чата в память (PageRequest Integer.MAX_VALUE).
    // При больших чатах стоит заменить на прицельный запрос диапазона по границам USER-сообщений.
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

        int start = targetIdx;
        for (int i = targetIdx - 1; i >= 0; i--) {
            if (MessageRole.USER.equals(all.get(i).getRole())) {
                start = i + 1;
                break;
            }
            if (i == 0) start = 0;
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

    @Override
    @Transactional(readOnly = true)
    public CaseChatHistoryResponse getCaseInterrogationChatHistory(Long caseId, Long interrogationId,
                                                                   String userEmail, int page, int size) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        CaseInterrogationCaseChat chat = caseInterrogationCaseChatRepository
                .findByInterrogationIdAndUserId(interrogationId, user.getId())
                .orElse(null);

        if (chat == null) {
            return CaseChatHistoryResponse.builder()
                    .messages(List.of())
                    .contradictions(List.of())
                    .totalMessages(0)
                    .build();
        }

        List<CaseChatMessage> messages = chatMessageRepository
                .findByCaseInterrogationCaseChatIdOrderByIdAsc(chat.getId(), PageRequest.of(page, size))
                .getContent();
        long total = chatMessageRepository.countByCaseInterrogationCaseChatId(chat.getId());

        return CaseChatHistoryResponse.builder()
                .chatId(chat.getId())
                .messages(messages.stream().map(CaseChatMessageDto::from).toList())
                .totalMessages((int) total)
                .currentPage(page)
                .pageSize(size)
                .lastMessageAt(chat.getLastMessageAt())
                .build();
    }

    @Override
    @Transactional
    public void clearCaseInterrogationChatHistory(Long caseId, Long interrogationId, String userEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        CaseInterrogationCaseChat chat = caseInterrogationCaseChatRepository
                .findByInterrogationIdAndUserId(interrogationId, user.getId())
                .orElseThrow(() -> new IllegalStateException("Чат не найден для допроса: " + interrogationId));

        chatMessageRepository.deleteAllByCaseInterrogationCaseChatId(chat.getId());
        chat.getMessages().clear();
        caseInterrogationCaseChatRepository.save(chat);

        logService.log(
                String.format("Cleared case interrogation chat by %s in case %s", userEmail, caseEntity.getNumber()),
                LogLevel.INFO, LogAction.CHAT_CLEAR, caseEntity.getNumber(), userEmail);
    }
}