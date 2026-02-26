package org.di.digital.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.ChatRequest;
import org.di.digital.dto.response.CaseChatHistoryResponse;
import org.di.digital.dto.response.CaseChatMessageDto;
import org.di.digital.model.*;
import org.di.digital.repository.*;
import org.di.digital.service.CaseInterrogationChatService;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseInterrogationChatServiceImpl implements CaseInterrogationChatService {

    private static final String INTERROGATION_ENDPOINT_TEMPLATE =
            "/interrogation/questions/%s";

    private static final String QUESTION_TEMPLATE =
            "Вопрос: %s\nОтвет: %s";

    private final WebClient.Builder webClientBuilder;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final InterrogationChatRepository interrogationChatRepository;
    private final CaseChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;

    @Value("${interrogation.model.host}")
    private String pythonHost;

    @Value("${interrogation.model.port}")
    private String interrogationChatPort;

    @Override
    @Transactional
    public void streamInterrogationChatResponse(Long caseId, Long interrogationId,
                                                ChatRequest request, String userEmail,
                                                SseEmitter emitter) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        validateAccess(caseEntity, user);

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        CaseInterrogationChat chat = getOrCreateChat(interrogation);

        // Save user message
        CaseChatMessage userMessage = CaseChatMessage.builder()
                .interrogationChat(chat)
                .role(MessageRole.USER)
                .content(request.getQuestion())
                .complete(true)
                .build();
        chat.addMessage(userMessage);
        chatMessageRepository.save(userMessage);

        CaseChatMessage assistantMessage = CaseChatMessage.builder()
                .interrogationChat(chat)
                .role(MessageRole.ASSISTANT)
                .content("")
                .complete(false)
                .build();
        chat.addMessage(assistantMessage);
        assistantMessage = chatMessageRepository.save(assistantMessage);

        StringBuilder fullResponse = new StringBuilder();
        final Long messageId = assistantMessage.getId();

        String url = buildIndictmentUrl(caseEntity.getNumber());
        Map<String, Object> body = buildRequestBody(interrogation.getFio(), interrogation.getRole(), request.getQuestion(), request.getAnswer());


        try {
            webClientBuilder.build()
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnNext(chunk -> handleInterrogationChunk(chunk, fullResponse, emitter, interrogationId))
                    .doOnComplete(() -> {
                        updateAssistantMessage(messageId, fullResponse.toString());
                        emitter.complete();
                        log.info("Chat streaming completed for interrogation {}, {} characters",
                                interrogationId, fullResponse.length());
                    })
                    .doOnError(error -> {
                        log.error("Streaming error for interrogation {}: ", interrogationId, error);
                        updateAssistantMessage(messageId, fullResponse + "\n[Error: " + error.getMessage() + "]");
                        emitter.completeWithError(error);
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("Error initializing chat stream for interrogation {}: ", interrogationId, e);
            emitter.completeWithError(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CaseChatHistoryResponse getChatHistory(Long caseId, Long interrogationId,
                                                  String userEmail, int page, int size) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        validateAccess(caseEntity, user);

        CaseInterrogationChat chat = interrogationChatRepository.findByInterrogationId(interrogationId)
                .orElse(null);

        if (chat == null) {
            return CaseChatHistoryResponse.builder()
                    .messages(List.of())
                    .totalMessages(0)
                    .build();
        }

        List<CaseChatMessage> messages = chatMessageRepository.findByInterrogationChatId(
                chat.getId(),
                PageRequest.of(page, size)
        ).getContent();

        long total = chatMessageRepository.countByInterrogationChatId(chat.getId());

        List<CaseChatMessageDto> messageDTOs = messages.stream()
                .map(CaseChatMessageDto::from)
                .toList();

        return CaseChatHistoryResponse.builder()
                .chatId(chat.getId())
                .messages(messageDTOs)
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
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        validateAccess(caseEntity, user);

        CaseInterrogationChat chat = interrogationChatRepository.findByInterrogationId(interrogationId)
                .orElseThrow(() -> new RuntimeException("Chat not found for interrogation: " + interrogationId));

        chatMessageRepository.deleteAllByInterrogationChatId(chat.getId());
        chat.getMessages().clear();
        interrogationChatRepository.save(chat);

        log.info("Cleared chat history for interrogation {}", interrogationId);
    }

    @Transactional
    protected CaseInterrogationChat getOrCreateChat(CaseInterrogation interrogation) {
        return interrogationChatRepository.findByInterrogationId(interrogation.getId())
                .orElseGet(() -> {
                    CaseInterrogationChat newChat = CaseInterrogationChat.builder()
                            .interrogation(interrogation)
                            .active(true)
                            .build();
                    log.info("Creating new chat for interrogation {}", interrogation.getId());
                    return interrogationChatRepository.save(newChat);
                });
    }

    @Transactional
    protected void updateAssistantMessage(Long messageId, String fullContent) {
        CaseChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found: " + messageId));
        message.setContent(fullContent);
        message.setComplete(true);
        chatMessageRepository.save(message);
    }
    private void handleInterrogationChunk(String chunk, StringBuilder fullResponse, SseEmitter emitter, Long interrogationId) {
        try {
            JsonNode node = objectMapper.readTree(chunk);
            if (node.has("questions")) {
                node.get("questions").forEach(question -> {
                    String q = question.asText();
                    fullResponse.append(q).append("\n");
                    try {
                        emitter.send(SseEmitter.event().data(q));
                    } catch (Exception e) {
                        log.error("Error sending question for interrogation {}: ", interrogationId, e);
                    }
                });
            }
        } catch (Exception e) {
            fullResponse.append(chunk);
            try {
                emitter.send(SseEmitter.event().data(chunk));
            } catch (Exception ex) {
                log.error("Error sending SSE chunk for interrogation {}: ", interrogationId, ex);
            }
        }
    }

    private void validateAccess(Case caseEntity, User user) {
        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            throw new AccessDeniedException("Access denied to case: " + caseEntity.getId());
        }
    }
    private Map<String, Object> buildRequestBody(String fio, String role, String question, String answer) {
        String lastQuestion = String.format(QUESTION_TEMPLATE, question, answer);
        log.info("LAST QUESTION: {}", lastQuestion);
        Map<String, Object> body = new HashMap<>();
        body.put("fio", fio);
        body.put("role", role);
        body.put("last_question", lastQuestion);
        return body;
    }
    private String buildIndictmentUrl(String caseNumber) {
        String endpoint = String.format(INTERROGATION_ENDPOINT_TEMPLATE, caseNumber);
        return UrlBuilder.buildUrl(pythonHost, interrogationChatPort, endpoint);
    }
}