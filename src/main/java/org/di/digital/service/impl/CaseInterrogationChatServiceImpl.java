package org.di.digital.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.ChatRequest;
import org.di.digital.dto.response.CaseChatHistoryResponse;
import org.di.digital.dto.response.CaseChatMessageDto;
import org.di.digital.model.*;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.repository.CaseChatMessageRepository;
import org.di.digital.repository.CaseRepository;
import org.di.digital.repository.InterrogationChatRepository;
import org.di.digital.repository.UserRepository;
import org.di.digital.service.CaseInterrogationChatService;
import org.di.digital.service.LogService;
import org.di.digital.service.StreamingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.di.digital.util.RequestBodyBuilder.interrogationBody;
import static org.di.digital.util.UrlBuilder.interrogationQuestionsUrl;
import static org.di.digital.util.UserUtil.validateUserAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseInterrogationChatServiceImpl implements CaseInterrogationChatService {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final InterrogationChatRepository interrogationChatRepository;
    private final CaseChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;
    private final StreamingService streamingService;
    private final LogService logService;

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
        validateUserAccess(caseEntity, user);

        String caseNumber = caseEntity.getNumber();
        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        CaseInterrogationChat chat = getOrCreateChat(interrogation);

        CaseChatMessage userMessage = CaseChatMessage.builder()
                .interrogationChat(chat)
                .role(MessageRole.USER)
                .isEdited(false)
                .content("Вопрос: " + request.getQuestion() + '\n' + "Ответ: " + request.getAnswer())
                .complete(true)
                .build();
        chat.addMessage(userMessage);
        chatMessageRepository.save(userMessage);

        CaseChatMessage placeholderMessage = CaseChatMessage.builder()
                .interrogationChat(chat)
                .role(MessageRole.ASSISTANT)
                .isEdited(false)
                .content("")
                .complete(false)
                .build();
        chat.addMessage(placeholderMessage);
        final Long placeholderId = chatMessageRepository.save(placeholderMessage).getId();

        List<CaseInterrogationQA> qaList = interrogation.getQaList();

        streamingService.stream(
                interrogationQuestionsUrl(pythonHost, interrogationChatPort, caseEntity.getNumber()),
                interrogationBody(interrogation.getFio(), interrogation.getRole(), qaList),
                emitter,
                this::extractInterrogationChunk,
                fullText -> {
                    chatMessageRepository.deleteById(placeholderId);

                    List<String> questions = parseQuestions(fullText);
                    if (questions.isEmpty()) {
                        CaseChatMessage single = CaseChatMessage.builder()
                                .interrogationChat(chat)
                                .role(MessageRole.ASSISTANT)
                                .isEdited(false)
                                .content(fullText)
                                .complete(true)
                                .build();
                        chatMessageRepository.save(single);
                    } else {
                        for (String question : questions) {
                            if (question.isBlank()) continue;
                            CaseChatMessage msg = CaseChatMessage.builder()
                                    .interrogationChat(chat)
                                    .role(MessageRole.ASSISTANT)
                                    .isEdited(false)
                                    .content(question.trim())
                                    .complete(true)
                                    .build();
                            chatMessageRepository.save(msg);
                        }
                    }

                    log.info("Saved {} question messages for interrogation {}",
                            questions.size(), interrogationId);
                },
                error -> {
                    log.error("Streaming error for interrogation {}: ", interrogationId, error);
                    updateAssistantMessage(placeholderId, "[Error: " + error.getMessage() + "]");
                }
        );

        logService.log(
                String.format("New interrogation chat message %s by %s user to case %s",
                        userMessage.getContent(), userEmail, caseNumber),
                LogLevel.INFO,
                LogAction.CHAT_MESSAGE,
                caseNumber,
                userEmail
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CaseChatHistoryResponse getChatHistory(Long caseId, Long interrogationId,
                                                  String userEmail, int page, int size) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        validateUserAccess(caseEntity, user);

        CaseInterrogationChat chat = interrogationChatRepository
                .findByInterrogationId(interrogationId).orElse(null);
        if (chat == null) {
            return CaseChatHistoryResponse.builder()
                    .messages(List.of())
                    .totalMessages(0)
                    .build();
        }

        List<CaseChatMessage> messages = chatMessageRepository
                .findByInterrogationChatIdOrderByIdAsc(chat.getId(), PageRequest.of(page, size))
                .getContent();
        long total = chatMessageRepository.countByInterrogationChatId(chat.getId());

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
    public void clearChatHistory(Long caseId, Long interrogationId, String userEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        validateUserAccess(caseEntity, user);

        String caseNumber = caseEntity.getNumber();
        CaseInterrogationChat chat = interrogationChatRepository.findByInterrogationId(interrogationId)
                .orElseThrow(() -> new RuntimeException("Chat not found for interrogation: " + interrogationId));

        chatMessageRepository.deleteAllByInterrogationChatId(chat.getId());
        chat.getMessages().clear();
        interrogationChatRepository.save(chat);

        logService.log(
                String.format("Cleared interrogation chat by %s user to case %s", userEmail, caseNumber),
                LogLevel.INFO,
                LogAction.CHAT_CLEAR,
                caseNumber,
                userEmail
        );
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
                    CaseInterrogationChat saved = interrogationChatRepository.save(newChat);
                    interrogationChatRepository.flush();
                    return saved;
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

    @Override
    @Transactional
    public void toggleMessageSelected(Long caseId, Long interrogationId, Long messageId, boolean selected, String userEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        validateUserAccess(caseEntity, user);

        CaseChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found: " + messageId));

        message.setIsSelected(selected);
        chatMessageRepository.save(message);
    }

    private List<String> parseQuestions(String fullText) {
        try {
            JsonNode node = objectMapper.readTree(fullText);
            if (node.has("questions")) {
                List<String> result = new ArrayList<>();
                node.get("questions").forEach(q -> result.add(q.asText()));
                return result;
            }
        } catch (Exception ignored) {}

        if (fullText.contains("\n")) {
            return Arrays.stream(fullText.split("\n"))
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        return List.of(fullText);
    }

    private String extractInterrogationChunk(String chunk) {
        try {
            JsonNode node = objectMapper.readTree(chunk);
            if (node.has("questions")) {
                StringBuilder sb = new StringBuilder();
                node.get("questions").forEach(q -> sb.append(q.asText()).append("\n"));
                return sb.toString();
            }
        } catch (Exception ignored) {}
        return chunk;
    }
}