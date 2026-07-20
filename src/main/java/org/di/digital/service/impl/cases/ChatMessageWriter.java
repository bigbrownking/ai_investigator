package org.di.digital.service.impl.cases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.cases.ReferenceDto;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseChat;
import org.di.digital.model.cases.CaseChatMessage;
import org.di.digital.model.enums.MessageRole;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseChatMessageRepository;
import org.di.digital.repository.cases.CaseChatRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageWriter {

    private final CaseChatRepository caseChatRepository;
    private final CaseChatMessageRepository chatMessageRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long createMessages(Long caseId, Long userId, String question) {
        CaseChat chat = getOrCreateChat(caseId, userId);

        CaseChatMessage userMessage = CaseChatMessage.builder()
                .chat(chat)
                .role(MessageRole.USER)
                .content(question)
                .complete(true)
                .build();
        chat.addMessage(userMessage);
        chatMessageRepository.save(userMessage);

        CaseChatMessage assistantMessage = CaseChatMessage.builder()
                .chat(chat)
                .role(MessageRole.ASSISTANT)
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

        log.info("Updated message {} with {} characters, {} references",
                messageId, content.length(), references != null ? references.size() : 0);
    }

    @Transactional
    public CaseChat getOrCreateChat(Long caseId, Long userId) {
        return caseChatRepository.findByCaseIdAndUserId(caseId, userId)
                .orElseGet(() -> {
                    Case c = caseRepository.findById(caseId)
                            .orElseThrow(() -> new IllegalStateException("Case not found: " + caseId));
                    User u = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
                    CaseChat newChat = CaseChat.builder()
                            .caseEntity(c).user(u).active(true).build();
                    log.info("Creating new chat for case {} and user {}", caseId, u.getEmail());
                    return caseChatRepository.save(newChat);
                });
    }

    @Transactional
    public void clearChatHistory(Long caseId, Long userId) {
        CaseChat chat = caseChatRepository.findByCaseIdAndUserId(caseId, userId)
                .orElseThrow(() -> new IllegalStateException("Chat not found for case: " + caseId));
        chatMessageRepository.deleteAllByChatId(chat.getId());
        chat.getMessages().clear();
        caseChatRepository.save(chat);
        log.info("Cleared chat history for case {} and user {}", caseId, userId);
    }
}