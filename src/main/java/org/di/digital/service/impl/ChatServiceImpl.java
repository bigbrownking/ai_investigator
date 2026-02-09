package org.di.digital.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.ChatRequest;
import org.di.digital.dto.response.CaseChatHistoryResponse;
import org.di.digital.dto.response.CaseChatMessageDto;
import org.di.digital.model.*;
import org.di.digital.model.enums.CaseActivityType;
import org.di.digital.repository.CaseChatMessageRepository;
import org.di.digital.repository.CaseChatRepository;
import org.di.digital.repository.CaseRepository;
import org.di.digital.repository.UserRepository;
import org.di.digital.service.CaseService;
import org.di.digital.service.ChatService;
import org.di.digital.util.UrlBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final WebClient.Builder webClientBuilder;
    private final CaseChatRepository caseChatRepository;
    private final CaseChatMessageRepository chatMessageRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseService caseService;
    private final ObjectMapper objectMapper;

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
        String url = UrlBuilder.buildUrl(pythonHost, chatPort, "/v1/chat/completions");

        log.info("🤖 Starting general chat stream for question: {}",
                request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));

        Map<String, Object> openAIRequest = new HashMap<>();
        openAIRequest.put("model", "qwen3-next-80b-instruct");
        openAIRequest.put("messages", List.of(
                Map.of("role", "user", "content", request.getQuestion())
        ));
        openAIRequest.put("max_tokens", 16324);
        openAIRequest.put("temperature", 0.2);
        openAIRequest.put("stream", true);

        StringBuilder fullResponse = new StringBuilder();

        try {
            webClientBuilder.build()
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(openAIRequest)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofMinutes(5))
                    .doOnNext(chunk -> {
                        try {
                            String content = extractContentFromSSE(chunk);
                            if (content != null) {
                                fullResponse.append(content);
                                emitter.send(SseEmitter.event().data(content));
                            }
                        } catch (Exception e) {
                            log.error("❌ Error sending SSE chunk: ", e);
                        }
                    })
                    .doOnComplete(() -> {
                        emitter.complete();
                        log.info("✅ General chat streaming completed, {} characters received",
                                fullResponse.length());
                    })
                    .doOnError(error -> {
                        log.error("❌ Streaming error: ", error);
                        emitter.completeWithError(error);
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("❌ Error initializing general chat streaming: ", e);
            emitter.completeWithError(e);
        }
    }

    private String extractContentFromSSE(String sseChunk) {
        if (sseChunk == null || sseChunk.trim().isEmpty()) {
            return null;
        }

        if (sseChunk.contains("[DONE]")) {
            return null;
        }

        try {
            String jsonStr = sseChunk.trim();

            if (jsonStr.startsWith("data: ")) {
                jsonStr = jsonStr.substring(6);
            }

            if (jsonStr.isEmpty() || jsonStr.equals("[DONE]")) {
                return null;
            }

            JsonNode node = objectMapper.readTree(jsonStr);

            if (node.has("choices") && node.get("choices").isArray() && !node.get("choices").isEmpty()) {
                JsonNode choice = node.get("choices").get(0);
                if (choice.has("delta") && choice.get("delta").has("content")) {
                    return choice.get("delta").get("content").asText();
                }
            }

            return null;

        } catch (Exception e) {
            log.debug("⚠️ Failed to parse SSE chunk: {}", sseChunk);
            return null;
        }
    }

    @Transactional
    @Override
    public void streamCaseChatResponseWithHistory(String caseNumber, ChatRequest request, String userEmail, SseEmitter emitter) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseNumber));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        validateUserAccess(caseEntity, user);

        CaseChat chat = getOrCreateChatForCaseAndUser(caseEntity.getId(), user.getId());

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

        String url = UrlBuilder.buildUrl(pythonHost, qualificationPort, "/chat/" + caseNumber);

        try {
            webClientBuilder.build().post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(enhancedRequest)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofMinutes(5))
                    .doOnNext(chunk -> {
                        log.info("CHUNK IS {}", chunk);
                        fullResponse.append(chunk);
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (Exception e) {
                            log.error("Error sending SSE chunk: ", e);
                        }
                    })
                    .doOnComplete(() -> {
                        updateAssistantMessage(messageId, fullResponse.toString());
                        caseService.updateCaseActivity(caseNumber, CaseActivityType.CHAT_MESSAGE.name());
                        emitter.complete();
                        log.info("Chat streaming completed for case {} (user: {}), {} characters received",
                                caseNumber, userEmail, fullResponse.length());
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

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        validateUserAccess(caseEntity, user);

        return getChatHistory(caseEntity.getId(), user.getId(), page, size);
    }

    @Transactional(readOnly = true)
    public CaseChatHistoryResponse getChatHistory(Long caseId, Long userId, int page, int size) {
        CaseChat chat = caseChatRepository.findByCaseIdAndUserId(caseId, userId)
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

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        // Users can only clear their own chat history
        validateUserAccess(caseEntity, user);

        clearChatHistory(caseEntity.getId(), user.getId());
    }

    @Transactional
    public void clearChatHistory(Long caseId, Long userId) {
        CaseChat chat = caseChatRepository.findByCaseIdAndUserId(caseId, userId)
                .orElseThrow(() -> new RuntimeException("Chat not found for case: " + caseId + " and user: " + userId));

        chatMessageRepository.deleteAllByChatId(chat.getId());
        chat.getMessages().clear();
        caseChatRepository.save(chat);

        log.info("🗑️ Cleared chat history for case {} and user {}", caseId, userId);
    }

    @Transactional
    protected CaseChat getOrCreateChatForCaseAndUser(Long caseId, Long userId) {
        return caseChatRepository.findByCaseIdAndUserId(caseId, userId)
                .orElseGet(() -> {
                    Case caseEntity = caseRepository.findById(caseId)
                            .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("User not found: " + userId));

                    CaseChat newChat = CaseChat.builder()
                            .caseEntity(caseEntity)
                            .user(user)
                            .active(true)
                            .build();

                    log.info("📝 Creating new chat for case {} and user {}", caseId, user.getEmail());
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

        log.info("💾 Updated message {} with {} characters", messageId, fullContent.length());
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

    private void validateUserAccess(Case caseEntity, User user) {
        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            log.warn("⛔ Access denied: User {} tried to access case {} chat", user.getEmail(), caseEntity.getNumber());
            throw new AccessDeniedException("You don't have permission to access this case's chat");
        }
    }
}