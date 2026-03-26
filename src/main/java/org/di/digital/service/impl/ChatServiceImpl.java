package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.constants.MessageConstant;
import org.di.digital.dto.request.ChatRequest;
import org.di.digital.dto.response.CaseChatHistoryResponse;
import org.di.digital.dto.response.CaseChatMessageDto;
import org.di.digital.model.*;
import org.di.digital.model.enums.CaseActivityType;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.repository.CaseChatMessageRepository;
import org.di.digital.repository.CaseChatRepository;
import org.di.digital.repository.CaseRepository;
import org.di.digital.repository.UserRepository;
import org.di.digital.service.CaseService;
import org.di.digital.service.ChatService;
import org.di.digital.service.LogService;
import org.di.digital.service.StreamingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.di.digital.util.RequestBodyBuilder.generalChatBody;
import static org.di.digital.util.UrlBuilder.generalChatUrl;
import static org.di.digital.util.UrlBuilder.qualificationChatUrl;
import static org.di.digital.util.UserUtil.validateUserAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final CaseChatRepository caseChatRepository;
    private final CaseChatMessageRepository chatMessageRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseService caseService;
    private final StreamingService streamingService;
    private final LogService logService;

    @Value("${qualification.model.host}")
    private String pythonHost;

    @Value("${qualification.model.port}")
    private String qualificationPort;

    @Value("${chat.model.port}")
    private String chatPort;

    @Value("${chat.context.max-messages:20}")
    private int maxContextMessages;

    @Override
    public void streamChatResponse(ChatRequest request, SseEmitter emitter) {
        log.info("Starting general chat stream for question: {}",
                request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));

        streamingService.stream(
                generalChatUrl(pythonHost, chatPort),
                generalChatBody(request.getQuestion()),
                emitter,
                this::extractOpenAIChunk,
                fullText -> log.info("General chat streaming completed, {} characters", fullText.length()),
                error -> log.error("General chat streaming error: ", error)
        );
    }

    private String extractOpenAIChunk(String sseChunk) {
        if (sseChunk == null || sseChunk.isBlank() || sseChunk.contains("[DONE]")) return null;
        try {
            String json = sseChunk.startsWith("data: ") ? sseChunk.substring(6) : sseChunk;
            if (json.isBlank() || json.equals("[DONE]")) return null;
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (node.has("choices") && node.get("choices").isArray() && !node.get("choices").isEmpty()) {
                var delta = node.get("choices").get(0).get("delta");
                if (delta != null && delta.has("content")) return delta.get("content").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Transactional
    @Override
    public void streamCaseChatResponseWithHistory(String caseNumber, ChatRequest request,
                                                  String userEmail, SseEmitter emitter) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseNumber));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        validateUserAccess(caseEntity, user);

        if (!caseEntity.isAtLeastOneFileProcessed()) {
            String message = MessageConstant.NO_FILE_PROCESSED.format(caseNumber);
            log.warn(message);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(message));
                emitter.complete();
            } catch (IOException e) {
                log.error("Failed to send error event", e);
                emitter.completeWithError(e);
            }
            return;
        }

        CaseChat chat = getOrCreateChatForCaseAndUser(caseEntity.getId(), user.getId());

        CaseChatMessage userMessage = CaseChatMessage.builder()
                .chat(chat).role(MessageRole.USER)
                .content(request.getQuestion()).complete(true).build();
        chat.addMessage(userMessage);
        chatMessageRepository.save(userMessage);

        CaseChatMessage assistantMessage = CaseChatMessage.builder()
                .chat(chat).role(MessageRole.ASSISTANT)
                .content("").complete(false).build();
        chat.addMessage(assistantMessage);
        final Long messageId = chatMessageRepository.save(assistantMessage).getId();

        ChatRequest enhancedRequest = buildRequestWithHistory(chat, request);

        streamingService.streamRaw(
                qualificationChatUrl(pythonHost, qualificationPort, caseNumber),
                enhancedRequest,
                emitter,
                fullText -> {
                    updateAssistantMessage(messageId, fullText);
                    caseService.updateCaseActivity(caseNumber, CaseActivityType.CHAT_MESSAGE.name());
                    log.info("Case chat streaming completed for case {} (user: {})", caseNumber, userEmail);
                },
                error -> {
                    log.error("Case chat streaming error for case {}: ", caseNumber, error);
                    updateAssistantMessage(messageId, "[Error: " + error.getMessage() + "]");
                }
        );
        logService.log(
                String.format("New chat message %s by %s user to case %s", userMessage.getId(), userEmail, caseNumber),
                LogLevel.INFO,
                LogAction.CHAT_MESSAGE,
                caseNumber,
                userEmail
        );
    }
    @Transactional(readOnly = true)
    public CaseChatHistoryResponse getChatHistoryByCaseNumber(String caseNumber, String userEmail, int page, int size) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseNumber));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        validateUserAccess(caseEntity, user);
        return getChatHistory(caseEntity.getId(), user.getId(), page, size);
    }

    @Transactional(readOnly = true)
    public CaseChatHistoryResponse getChatHistory(Long caseId, Long userId, int page, int size) {
        CaseChat chat = caseChatRepository.findByCaseIdAndUserId(caseId, userId).orElse(null);
        if (chat == null) return CaseChatHistoryResponse.builder().messages(List.of()).totalMessages(0).build();

        List<CaseChatMessage> messages = chatMessageRepository.findByChatId(chat.getId(), PageRequest.of(page, size)).getContent();
        long total = chatMessageRepository.countByChatId(chat.getId());

        return CaseChatHistoryResponse.builder()
                .chatId(chat.getId()).caseId(caseId)
                .messages(messages.stream().map(CaseChatMessageDto::from).toList())
                .totalMessages((int) total).currentPage(page).pageSize(size)
                .lastMessageAt(chat.getLastMessageAt()).build();
    }

    @Transactional
    public void clearChatHistoryByCaseNumber(String caseNumber, String userEmail) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseNumber));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        validateUserAccess(caseEntity, user);
        clearChatHistory(caseEntity.getId(), user.getId());
        logService.log(
                String.format("Cleared chat by %s user to case %s", userEmail, caseNumber),
                LogLevel.INFO,
                LogAction.CHAT_CLEAR,
                caseNumber,
                userEmail
        );
    }

    @Transactional
    public void clearChatHistory(Long caseId, Long userId) {
        CaseChat chat = caseChatRepository.findByCaseIdAndUserId(caseId, userId)
                .orElseThrow(() -> new RuntimeException("Chat not found for case: " + caseId));
        chatMessageRepository.deleteAllByChatId(chat.getId());
        chat.getMessages().clear();
        caseChatRepository.save(chat);
        log.info("Cleared chat history for case {} and user {}", caseId, userId);
    }

    @Transactional
    protected CaseChat getOrCreateChatForCaseAndUser(Long caseId, Long userId) {
        return caseChatRepository.findByCaseIdAndUserId(caseId, userId)
                .orElseGet(() -> {
                    Case c = caseRepository.findById(caseId)
                            .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));
                    User u = userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
                    CaseChat newChat = CaseChat.builder().caseEntity(c).user(u).active(true).build();
                    log.info("Creating new chat for case {} and user {}", caseId, u.getEmail());
                    return caseChatRepository.save(newChat);
                });
    }

    @Transactional
    protected void updateAssistantMessage(Long messageId, String fullContent) {
        CaseChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found: " + messageId));
        message.setContent(fullContent);
        message.setComplete(true);
        chatMessageRepository.save(message);
        log.info("Updated message {} with {} characters", messageId, fullContent.length());
    }

    private ChatRequest buildRequestWithHistory(CaseChat chat, ChatRequest request) {
        List<CaseChatMessage> recentMessages = chatMessageRepository.findRecentMessages(
                chat.getId(), PageRequest.of(0, maxContextMessages));
        Collections.reverse(recentMessages);
        return ChatRequest.builder().question(request.getQuestion()).stream(request.getStream()).build();
    }
}