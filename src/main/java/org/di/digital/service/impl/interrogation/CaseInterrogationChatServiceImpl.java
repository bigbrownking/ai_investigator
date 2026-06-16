package org.di.digital.service.impl.interrogation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.cases.ChatRequest;
import org.di.digital.dto.response.chat.CaseChatHistoryResponse;
import org.di.digital.dto.response.chat.CaseChatMessageDto;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseChatMessage;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.model.enums.MessageRole;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.interrogation.CaseInterrogationCaseChat;
import org.di.digital.model.interrogation.CaseInterrogationChat;
import org.di.digital.model.interrogation.CaseInterrogationQA;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseChatMessageRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.interrogation.CaseInterrogationCaseChatRepository;
import org.di.digital.repository.interrogation.CaseInterrogationChatRepository;
import org.di.digital.repository.user.UserRepository;
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

import static org.di.digital.util.requests.RequestBodyBuilder.interrogationBody;
import static org.di.digital.util.requests.RequestUrlBuilder.interrogationQuestionsUrl;
import static org.di.digital.util.requests.RequestUrlBuilder.qualificationChatUrl;
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
    private final ObjectMapper objectMapper;
    private final StreamingService streamingService;
    private final LogService logService;

    @Value("${model.host}")
    private String pythonHost;

    @Value("${interrogation.port}")
    private String interrogationChatPort;

    @Value("${qualification.port}")
    private String chatPort;

    @Override
    @Transactional
    public void streamInterrogationChatResponse(Long caseId, Long interrogationId,
                                                ChatRequest request, String userEmail,
                                                SseEmitter emitter) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        String caseNumber = caseEntity.getNumber();
        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Допрос не найден: " + interrogationId));

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

        String language = interrogation.getLanguage().equals("русском") ? "russian" : "kazakh";
        log.info("Language is {}", language);
        streamingService.stream(
                interrogationQuestionsUrl(pythonHost, interrogationChatPort, caseEntity.getNumber()),
                interrogationBody(interrogation.getFio(), interrogation.getRole(),language, qaList),
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
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        CaseInterrogationChat chat = caseInterrogationChatRepository
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
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        String caseNumber = caseEntity.getNumber();
        CaseInterrogationChat chat = caseInterrogationChatRepository.findByInterrogationId(interrogationId)
                .orElseThrow(() -> new IllegalStateException("Чат для допроса не найден: " + interrogationId));

        chatMessageRepository.deleteAllByInterrogationChatId(chat.getId());
        chat.getMessages().clear();
        caseInterrogationChatRepository.save(chat);

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
        return caseInterrogationChatRepository.findByInterrogationId(interrogation.getId())
                .orElseGet(() -> {
                    CaseInterrogationChat newChat = CaseInterrogationChat.builder()
                            .interrogation(interrogation)
                            .active(true)
                            .build();
                    log.info("Creating new chat for interrogation {}", interrogation.getId());
                    CaseInterrogationChat saved = caseInterrogationChatRepository.save(newChat);
                    caseInterrogationChatRepository.flush();
                    return saved;
                });
    }

    @Transactional
    protected void updateAssistantMessage(Long messageId, String fullContent) {
        CaseChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("Message not found: " + messageId));
        message.setContent(fullContent);
        message.setComplete(true);
        chatMessageRepository.save(message);
    }

    @Override
    @Transactional
    public void toggleMessageSelected(Long caseId, Long interrogationId, Long messageId, boolean selected, String userEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        CaseChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("Сообщение не найдено: " + messageId));

        message.setIsSelected(selected);
        logService.log(
                String.format("Message %s select in case %s", message.getContent(), caseEntity.getNumber()),
                LogLevel.INFO,
                LogAction.MESSAGE_SELECTED,
                caseEntity.getNumber(),
                user.getEmail()
        );
        chatMessageRepository.save(message);
    }


    @Override
    @Transactional
    public void streamCaseInterrogationChatResponse(Long caseId, Long interrogationId,
                                                    ChatRequest request, String userEmail,
                                                    SseEmitter emitter) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

        CaseInterrogationCaseChat chat = getOrCreateCaseInterrogationChat(interrogation, user);

        CaseChatMessage userMessage = CaseChatMessage.builder()
                .caseInterrogationCaseChat(chat)
                .role(MessageRole.USER)
                .isEdited(false)
                .content(request.getQuestion())
                .complete(true)
                .build();
        chat.addMessage(userMessage);
        chatMessageRepository.save(userMessage);

        CaseChatMessage assistantMessage = CaseChatMessage.builder()
                .caseInterrogationCaseChat(chat)
                .role(MessageRole.ASSISTANT)
                .isEdited(false)
                .content("")
                .complete(false)
                .build();
        chat.addMessage(assistantMessage);
        final Long messageId = chatMessageRepository.save(assistantMessage).getId();

        streamingService.stream(
                qualificationChatUrl(pythonHost, chatPort, caseEntity.getNumber()),
                request,
                emitter,
                chunk -> chunk,
                fullText -> {
                    updateAssistantMessage(messageId, fullText);
                    log.info("Case interrogation chat streaming completed for interrogation {}", interrogationId);
                },
                error -> {
                    log.error("Case interrogation chat streaming error for interrogation {}: ", interrogationId, error);
                    updateAssistantMessage(messageId, "[Error: " + error.getMessage() + "]");
                },
                true
        );

        logService.log(
                String.format("New case interrogation chat message by %s in case %s", userEmail, caseEntity.getNumber()),
                LogLevel.INFO, LogAction.CHAT_MESSAGE, caseEntity.getNumber(), userEmail
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CaseChatHistoryResponse getCaseInterrogationChatHistory(Long caseId, Long interrogationId,
                                                                   String userEmail, int page, int size) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        CaseInterrogationCaseChat chat = caseInterrogationCaseChatRepository
                .findByInterrogationIdAndUserId(interrogationId, user.getId())
                .orElse(null);

        if (chat == null) {
            return CaseChatHistoryResponse.builder()
                    .messages(List.of())
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
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        CaseInterrogationCaseChat chat = caseInterrogationCaseChatRepository
                .findByInterrogationIdAndUserId(interrogationId, user.getId())
                .orElseThrow(() -> new RuntimeException("Чат не найден для допроса: " + interrogationId));

        chatMessageRepository.deleteAllByCaseInterrogationCaseChatId(chat.getId());
        chat.getMessages().clear();
        caseInterrogationCaseChatRepository.save(chat);

        logService.log(
                String.format("Cleared case interrogation chat by %s in case %s", userEmail, caseEntity.getNumber()),
                LogLevel.INFO, LogAction.CHAT_CLEAR, caseEntity.getNumber(), userEmail
        );
    }

    @Transactional
    protected CaseInterrogationCaseChat getOrCreateCaseInterrogationChat(CaseInterrogation interrogation, User user) {
        return caseInterrogationCaseChatRepository
                .findByInterrogationIdAndUserId(interrogation.getId(), user.getId())
                .orElseGet(() -> {
                    CaseInterrogationCaseChat newChat = CaseInterrogationCaseChat.builder()
                            .interrogation(interrogation)
                            .user(user)
                            .active(true)
                            .build();
                    log.info("Creating new case chat for interrogation {} and user {}",
                            interrogation.getId(), user.getEmail());
                    return caseInterrogationCaseChatRepository.save(newChat);
                });
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