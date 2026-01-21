package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.ChatRequest;
import org.di.digital.dto.response.CaseChatHistoryResponse;
import org.di.digital.dto.response.CaseChatMessageDto;
import org.di.digital.model.*;
import org.di.digital.repository.CaseChatMessageRepository;
import org.di.digital.repository.CaseChatRepository;
import org.di.digital.repository.CaseRepository;
import org.di.digital.repository.UserRepository;
import org.di.digital.service.ChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final WebClient.Builder webClientBuilder;
    private final CaseChatRepository caseChatRepository;
    private final CaseChatMessageRepository chatMessageRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;

    @Value("${python.model.host}")
    private String pythonHost;

    @Value("${python.model.port}")
    private String pythonPort;

    @Value("${chat.context.max-messages:20}")
    private int maxContextMessages;

    @Transactional
    public void streamChatResponseWithHistory(String caseNumber, ChatRequest request, String userEmail, SseEmitter emitter) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseNumber));

        validateUserAccess(caseEntity, userEmail);

        CaseChat chat = getOrCreateChatForCase(caseEntity.getId());

        CaseChatMessage userMessage = CaseChatMessage.builder()
                .chat(chat)
                .role(MessageRole.USER)
                .content(request.getQuestion())
                .complete(true)
                .build();
        chat.addMessage(userMessage);
        chatMessageRepository.save(userMessage);

        ChatRequest enhancedRequest = buildRequestWithHistory(chat, request);

        CaseChatMessage assistantMessage = CaseChatMessage.builder()
                .chat(chat)
                .role(MessageRole.ASSISTANT)
                .content("")
                .complete(false)
                .build();

        chat.addMessage(assistantMessage);
        assistantMessage = chatMessageRepository.save(assistantMessage);

        StringBuilder fullResponse = new StringBuilder();
        final Long messageId = assistantMessage.getId();

        String url = buildUrl("/chat/" + caseNumber);

        try {
            WebClient webClient = webClientBuilder.build();

            webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(enhancedRequest)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofMinutes(5))
                    .doOnNext(chunk -> {
                        fullResponse.append(chunk);
                        try {
                            emitter.send(SseEmitter.event()
                                    .data(chunk)
                                    .name("message"));
                        } catch (Exception e) {
                            log.error("Error sending SSE chunk: ", e);
                        }
                    })
                    .doOnComplete(() -> {
                        updateAssistantMessage(messageId, fullResponse.toString());
                        emitter.complete();
                        log.info("Chat streaming completed for case {}, {} characters received",
                                caseNumber, fullResponse.length());
                    })
                    .doOnError(error -> {
                        log.error("Streaming error for case {}: ", caseNumber, error);
                        updateAssistantMessage(messageId, fullResponse + "\n[Error: " + error.getMessage() + "]");
                        emitter.completeWithError(error);
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("Error initializing chat streaming for case {}: ", caseNumber, e);
            emitter.completeWithError(e);
        }
    }

    @Transactional(readOnly = true)
    public CaseChatHistoryResponse getChatHistoryByCaseNumber(String caseNumber, String userEmail, int page, int size) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseNumber));

        validateUserAccess(caseEntity, userEmail);

        return getChatHistory(caseEntity.getId(), userEmail, page, size);
    }

    @Transactional(readOnly = true)
    public CaseChatHistoryResponse getChatHistory(Long caseId, String userEmail, int page, int size) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        validateUserAccess(caseEntity, userEmail);

        CaseChat chat = caseChatRepository.findByCaseId(caseId)
                .orElse(null);

        if (chat == null) {
            return CaseChatHistoryResponse.builder()
                    .messages(List.of())
                    .totalMessages(0)
                    .build();
        }

        List<CaseChatMessage> messages = chatMessageRepository.findByChatId(
                chat.getId(),
                PageRequest.of(page, size)
        ).getContent();

        long total = chatMessageRepository.countByChatId(chat.getId());

        List<CaseChatMessageDto> messageDTOs = messages.stream()
                .map(CaseChatMessageDto::from)
                .toList();

        return CaseChatHistoryResponse.builder()
                .chatId(chat.getId())
                .caseId(caseId)
                .messages(messageDTOs)
                .totalMessages((int) total)
                .currentPage(page)
                .pageSize(size)
                .lastMessageAt(chat.getLastMessageAt())
                .build();
    }

    @Transactional
    public void clearChatHistoryByCaseNumber(String caseNumber, String userEmail) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseNumber));

        validateOwnerAccess(caseEntity, userEmail);

        clearChatHistory(caseEntity.getId());
    }

    @Transactional
    public void clearChatHistory(Long caseId) {
        CaseChat chat = caseChatRepository.findByCaseId(caseId)
                .orElseThrow(() -> new RuntimeException("Chat not found for case: " + caseId));

        chatMessageRepository.deleteAllByChatId(chat.getId());
        chat.getMessages().clear();
        caseChatRepository.save(chat);

        log.info("Cleared chat history for case {}", caseId);
    }

    @Transactional
    protected CaseChat getOrCreateChatForCase(Long caseId) {
        return caseChatRepository.findByCaseId(caseId)
                .orElseGet(() -> {
                    Case caseEntity = caseRepository.findById(caseId)
                            .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

                    CaseChat newChat = CaseChat.builder()
                            .caseEntity(caseEntity)
                            .active(true)
                            .build();

                    log.info("Creating new chat for case {}", caseId);
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
                chat.getId(),
                PageRequest.of(0, maxContextMessages)
        );

        Collections.reverse(recentMessages);

        return ChatRequest.builder()
                .question(request.getQuestion())
                .stream(request.getStream())
                .build();
    }
    private void validateUserAccess(Case caseEntity, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            log.warn("Access denied: User {} tried to access case {} chat", userEmail, caseEntity.getNumber());
            throw new AccessDeniedException("You don't have permission to access this case's chat");
        }
    }

    private void validateOwnerAccess(Case caseEntity, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        if (!caseEntity.isOwner(user)) {
            log.warn("Access denied: User {} tried to clear chat for case {}", userEmail, caseEntity.getNumber());
            throw new AccessDeniedException("Only the case owner can clear chat history");
        }
    }

    private String buildUrl(String endpoint) {
        return String.format("http://%s:%s%s", pythonHost, pythonPort, endpoint);
    }
}