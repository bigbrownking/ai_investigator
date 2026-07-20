package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseChatMessage;
import org.di.digital.model.enums.MessageRole;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.interrogation.CaseInterrogationChat;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseChatMessageRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.interrogation.CaseInterrogationChatRepository;
import org.di.digital.repository.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.di.digital.util.requests.RequestBodyBuilder.interrogationBody;
import static org.di.digital.util.requests.UserUtil.validateUserAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterrogationQuestionsWriter {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseInterrogationChatRepository caseInterrogationChatRepository;
    private final CaseChatMessageRepository chatMessageRepository;

    @Transactional
    public PreparedInterrogation prepare(Long caseId, Long interrogationId,
                                         String question, String answer, String userEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Допрос не найден: " + interrogationId));

        CaseInterrogationChat chat = getOrCreateChat(interrogation);

        CaseChatMessage userMessage = CaseChatMessage.builder()
                .interrogationChat(chat)
                .role(MessageRole.USER)
                .isEdited(false)
                .content("Вопрос: " + question + '\n' + "Ответ: " + answer)
                .complete(true)
                .build();
        chat.addMessage(userMessage);
        chatMessageRepository.save(userMessage);

        CaseChatMessage placeholder = CaseChatMessage.builder()
                .interrogationChat(chat)
                .role(MessageRole.ASSISTANT)
                .isEdited(false)
                .content("")
                .complete(false)
                .build();
        chat.addMessage(placeholder);
        Long placeholderId = chatMessageRepository.save(placeholder).getId();

        String language = "русском".equals(interrogation.getLanguage()) ? "russian" : "kazakh";
        Object requestBody = interrogationBody(
                interrogation.getFio(),
                interrogation.getRole(),
                language,
                interrogation.getQaList());

        return new PreparedInterrogation(
                chat.getId(),
                placeholderId,
                caseEntity.getNumber(),
                requestBody,
                userMessage.getContent()
        );
    }

    @Transactional
    public void saveQuestions(Long chatId, Long placeholderId, java.util.List<String> questions) {
        CaseInterrogationChat chat = caseInterrogationChatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("Чат не найден: " + chatId));

        chatMessageRepository.deleteById(placeholderId);

        if (questions.isEmpty()) {
            CaseChatMessage single = CaseChatMessage.builder()
                    .interrogationChat(chat)
                    .role(MessageRole.ASSISTANT)
                    .isEdited(false)
                    .content("")
                    .complete(true)
                    .build();
            chatMessageRepository.save(single);
        } else {
            for (String q : questions) {
                if (q.isBlank()) continue;
                CaseChatMessage msg = CaseChatMessage.builder()
                        .interrogationChat(chat)
                        .role(MessageRole.ASSISTANT)
                        .isEdited(false)
                        .content(q.trim())
                        .complete(true)
                        .build();
                chatMessageRepository.save(msg);
            }
        }
    }

    @Transactional
    public void markError(Long placeholderId, String errorText) {
        chatMessageRepository.findById(placeholderId).ifPresent(msg -> {
            msg.setContent(errorText);
            msg.setComplete(true);
            chatMessageRepository.save(msg);
        });
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

    public record PreparedInterrogation(
            Long chatId,
            Long placeholderId,
            String caseNumber,
            Object requestBody,
            String userMessageContent
    ) {}
}