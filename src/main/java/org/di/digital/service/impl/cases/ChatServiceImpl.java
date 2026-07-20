package org.di.digital.service.impl.cases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.cases.ChatRequest;
import org.di.digital.dto.response.cases.QueryResponse;
import org.di.digital.dto.response.chat.CaseChatHistoryResponse;
import org.di.digital.dto.response.chat.CaseChatMessageDto;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseChat;
import org.di.digital.model.cases.CaseChatMessage;
import org.di.digital.model.enums.*;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseChatMessageRepository;
import org.di.digital.repository.cases.CaseChatRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.CaseService;
import org.di.digital.service.ChatService;
import org.di.digital.service.LogService;
import org.di.digital.service.impl.core.sse.SseTypingEmitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.di.digital.util.requests.RequestBodyBuilder.generalChatBody;
import static org.di.digital.util.requests.RequestUrlBuilder.generalChatUrl;
import static org.di.digital.util.requests.RequestUrlBuilder.qualificationChatUrl;
import static org.di.digital.util.requests.UserUtil.validateUserAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final CaseChatRepository caseChatRepository;
    private final CaseChatMessageRepository chatMessageRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseService caseService;
    private final LogService logService;
    private final ChatMessageWriter chatMessageWriter;
    private final WebClient.Builder webClientBuilder;
    private final SseTypingEmitter sseTypingEmitter;

    @Value("${model.host}")
    private String pythonHost;

    @Value("${qualification.port}")
    private String qualificationPort;

    @Value("${chat.port}")
    private String chatPort;

    @Override
    public void streamChatResponse(ChatRequest request, SseEmitter emitter) {
        String question = request.getQuestion();
        log.info("Starting general chat for question: {}",
                question.substring(0, Math.min(50, question.length())));

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        CompletableFuture.runAsync(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes);
            try {
                QueryResponse response = webClientBuilder.build()
                        .post()
                        .uri(generalChatUrl(pythonHost, chatPort))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(generalChatBody(question))
                        .retrieve()
                        .bodyToMono(QueryResponse.class)
                        .block();

                if (response == null || response.getResponse() == null) {
                    throw new IllegalStateException("Пустой ответ от сервиса");
                }

                emitter.send(SseEmitter.event().name("message").data(response.getResponse()));
                emitter.complete();

                log.info("General chat completed, {} characters, {} references",
                        response.getResponse().length(),
                        response.getReferences() != null ? response.getReferences().size() : 0);

            } catch (Exception e) {
                log.error("General chat error: ", e);
                emitter.completeWithError(e);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
    }

    @Override
    public void streamCaseChatResponseWithHistory(String caseNumber, ChatRequest request,
                                                  String userEmail, SseEmitter emitter) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        if (!caseEntity.isAtLeastOneFileProcessed()) {
            String message = MessageConstant.NO_FILE_PROCESSED.format(caseNumber);
            log.warn(message);
            try {
                emitter.send(SseEmitter.event().name("error").data(message));
                emitter.complete();
            } catch (IOException e) {
                log.error("Failed to send error event", e);
                emitter.completeWithError(e);
            }
            logService.log(
                    String.format("No file processed for chat request in case %s", caseNumber),
                    LogLevel.ERROR,
                    LogAction.NO_FILE_PROCESSED,
                    caseNumber,
                    user.getEmail()
            );
            return;
        }

        final Long messageId = chatMessageWriter.createMessages(
                caseEntity.getId(), user.getId(), request.getQuestion());

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        CompletableFuture.runAsync(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes);
            try {
                QueryResponse response = webClientBuilder.build()
                        .post()
                        .uri(qualificationChatUrl(pythonHost, qualificationPort, caseNumber))
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

                chatMessageWriter.updateAssistantMessage(
                        messageId, response.getResponse(), response.getReferences());

                caseService.updateCaseActivity(caseNumber, CaseActivityType.CHAT_MESSAGE.getDescription());

                logService.log(
                        String.format("New chat message %s by %s user to case %s",
                                request.getQuestion(), userEmail, caseNumber),
                        LogLevel.INFO, LogAction.CHAT_MESSAGE, caseNumber, userEmail);

                log.info("Case chat completed for case {} (user: {})", caseNumber, userEmail);

            } catch (Exception e) {
                log.error("Case chat error for case {}: ", caseNumber, e);
                chatMessageWriter.updateAssistantMessage(
                        messageId, "[Error: " + e.getMessage() + "]", null);
                emitter.completeWithError(e);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public CaseChatHistoryResponse getChatHistoryByCaseNumber(String caseNumber, String userEmail,
                                                              int page, int size) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);
        return getChatHistory(caseEntity.getId(), user.getId(), page, size);
    }

    @Transactional(readOnly = true)
    public CaseChatHistoryResponse getChatHistory(Long caseId, Long userId, int page, int size) {
        CaseChat chat = caseChatRepository.findByCaseIdAndUserId(caseId, userId).orElse(null);
        if (chat == null) {
            return CaseChatHistoryResponse.builder().messages(List.of()).totalMessages(0).build();
        }

        List<CaseChatMessage> messages = chatMessageRepository
                .findByChatId(chat.getId(), PageRequest.of(page, size)).getContent();
        long total = chatMessageRepository.countByChatId(chat.getId());

        return CaseChatHistoryResponse.builder()
                .chatId(chat.getId())
                .caseId(caseId)
                .messages(messages.stream().map(CaseChatMessageDto::from).toList())
                .totalMessages((int) total)
                .currentPage(page)
                .pageSize(size)
                .lastMessageAt(chat.getLastMessageAt())
                .build();
    }

    @Override
    @Transactional
    public void clearChatHistoryByCaseNumber(String caseNumber, String userEmail) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        chatMessageWriter.clearChatHistory(caseEntity.getId(), user.getId());

        logService.log(
                String.format("Cleared chat by %s user to case %s", userEmail, caseNumber),
                LogLevel.INFO,
                LogAction.CHAT_CLEAR,
                caseNumber,
                userEmail
        );
    }
}