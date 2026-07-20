package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.cases.ReferenceDto;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseChatMessage;
import org.di.digital.model.enums.MessageRole;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.interrogation.CaseInterrogationCaseChat;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseChatMessageRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.interrogation.CaseInterrogationCaseChatRepository;
import org.di.digital.repository.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.di.digital.util.requests.UserUtil.validateUserAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterrogationChatWriter {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseChatMessageRepository chatMessageRepository;
    private final CaseInterrogationCaseChatRepository caseChatRepository;

    @Transactional
    public PreparedCaseChat prepareCaseChat(Long caseId, Long interrogationId,
                                            String userEmail, String question) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userEmail));
        validateUserAccess(caseEntity, user);

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Допрос не найден: " + interrogationId));

        Long messageId = createCaseChatMessages(interrogation, user, question);
        return new PreparedCaseChat(messageId, caseEntity.getNumber());
    }

    @Transactional
    public Long createCaseChatMessages(CaseInterrogation interrogation, User user, String question) {
        CaseInterrogationCaseChat chat = caseChatRepository
                .findByInterrogationIdAndUserId(interrogation.getId(), user.getId())
                .orElseGet(() -> {
                    log.info("Creating new case chat for interrogation {} and user {}",
                            interrogation.getId(), user.getEmail());
                    return caseChatRepository.save(CaseInterrogationCaseChat.builder()
                            .interrogation(interrogation)
                            .user(user)
                            .active(true)
                            .build());
                });

        CaseChatMessage userMessage = CaseChatMessage.builder()
                .caseInterrogationCaseChat(chat)
                .role(MessageRole.USER)
                .isEdited(false)
                .content(question)
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

        return chatMessageRepository.save(assistantMessage).getId();
    }

    @Transactional
    public void updateAssistantMessage(Long messageId, String content,
                                       List<ReferenceDto> references) {
        CaseChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("Message not found: " + messageId));
        message.setContent(content);
        message.setReferences(references);
        message.setComplete(true);
        chatMessageRepository.save(message);
    }

    public record PreparedCaseChat(Long messageId, String caseNumber) {}
}