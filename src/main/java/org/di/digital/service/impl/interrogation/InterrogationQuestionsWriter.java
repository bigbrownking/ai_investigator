package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseChatMessage;
import org.di.digital.model.enums.cases.MessageRole;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.interrogation.CaseInterrogationChat;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseChatMessageRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.interrogation.CaseInterrogationChatRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.cases.CaseAccessService;
import org.di.digital.util.requests.UserUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.di.digital.util.requests.RequestBodyBuilder.interrogationBody;
import static org.di.digital.util.requests.UserUtil.getCurrentLang;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterrogationQuestionsWriter {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseInterrogationChatRepository caseInterrogationChatRepository;
    private final CaseChatMessageRepository chatMessageRepository;
    private final CaseAccessService caseAccessService;
    private final UserUtil userUtil;

    @Transactional
    public PreparedInterrogation prepare(Long caseId, Long interrogationId,
                                         String question, String answer, String userEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), String.valueOf(caseId))));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userEmail)));
        userUtil.validateUserAccess(caseEntity, user);
        caseAccessService.require(caseEntity, user, CaseModule.CHAT, CaseAction.ADD);

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.INTERROGATION.localized(currentLang(), interrogationId.toString())));

        CaseInterrogationChat chat = getOrCreateChat(interrogation);

        CaseChatMessage userMessage = CaseChatMessage.builder()
                .interrogationChat(chat)
                .role(MessageRole.USER)
                .isEdited(false)
                .content("Вопрос: " + question + '\n' + "Ответ: " + answer)
                .complete(true)
                .build();
        chat.addMessage(userMessage);
        Long userMessageId = chatMessageRepository.save(userMessage).getId();

        CaseChatMessage placeholder = CaseChatMessage.builder()
                .interrogationChat(chat)
                .role(MessageRole.ASSISTANT)
                .isEdited(false)
                .content("")
                .complete(false)
                .build();
        chat.addMessage(placeholder);
        Long placeholderId = chatMessageRepository.save(placeholder).getId();

        String language = interrogation.getAdequateLanguage();
        Object requestBody = interrogationBody(
                interrogation.getFio(),
                interrogation.getRole(),
                language,
                interrogation.getQaList());

        return new PreparedInterrogation(
                chat.getId(),
                placeholderId,
                userMessageId,
                caseEntity.getNumber(),
                requestBody,
                userMessage.getContent(),
                answer,
                language,
                interrogation.getFio()
        );
    }

    @Transactional
    public void saveQuestions(Long chatId, Long placeholderId, java.util.List<String> questions) {
        CaseInterrogationChat chat = caseInterrogationChatRepository.findById(chatId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CHAT.localized(currentLang(), chatId.toString())));

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
            Long userMessageId,
            String caseNumber,
            Object requestBody,
            String userMessageContent,
            String answer,
            String language,
            String fio
    ) {}

    private UserSettingsLanguage currentLang() {
        return getCurrentLang();
    }
}