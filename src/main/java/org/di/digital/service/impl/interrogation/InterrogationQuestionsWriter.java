package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseChatMessage;
import org.di.digital.model.enums.cases.MessageRole;
import org.di.digital.model.enums.interrogation.QAStatusEnum;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.interrogation.CaseInterrogationChat;
import org.di.digital.model.interrogation.CaseInterrogationQA;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseChatMessageRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.interrogation.CaseInterrogationChatRepository;
import org.di.digital.repository.interrogation.CaseInterrogationQARepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.cases.CaseAccessService;
import org.di.digital.util.requests.UserUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    private final CaseInterrogationQARepository caseInterrogationQARepository;
    private final CaseAccessService caseAccessService;
    private final UserUtil userUtil;

    @Transactional
    public PreparedInterrogation prepare(Long caseId, Long interrogationId, Long qaId,
                                         String question, String answer, String userEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), String.valueOf(caseId))));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userEmail)));
        userUtil.validateUserAccess(caseEntity, user);
        caseAccessService.require(caseEntity, user, CaseModule.INTERROGATION, CaseAction.UPDATE);

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.INTERROGATION.localized(currentLang(), interrogationId.toString())));

        CaseInterrogationQA qa = upsertQa(interrogation, qaId, question, answer);

        CaseInterrogationChat chat = getOrCreateChat(interrogation);

        CaseChatMessage userMessage = CaseChatMessage.builder()
                .interrogationChat(chat)
                .role(MessageRole.USER)
                .isEdited(false)
                .content("Вопрос: " + nullToEmpty(question) + '\n' + "Ответ: " + nullToEmpty(answer))
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
                qa != null ? qa.getId() : null,
                caseEntity.getNumber(),
                requestBody,
                userMessage.getContent(),
                answer,
                language,
                interrogation.getFio()
        );
    }

    @Transactional
    public void saveQuestions(Long chatId, Long placeholderId, List<String> questions) {
        CaseInterrogationChat chat = caseInterrogationChatRepository.findById(chatId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CHAT.localized(currentLang(), chatId.toString())));

        chatMessageRepository.deleteById(placeholderId);

        if (questions.isEmpty()) {
            chatMessageRepository.save(CaseChatMessage.builder()
                    .interrogationChat(chat)
                    .role(MessageRole.ASSISTANT)
                    .isEdited(false)
                    .content("")
                    .complete(true)
                    .build());
            return;
        }

        for (String q : questions) {
            if (q == null || q.isBlank()) continue;
            chatMessageRepository.save(CaseChatMessage.builder()
                    .interrogationChat(chat)
                    .role(MessageRole.ASSISTANT)
                    .isEdited(false)
                    .content(q.trim())
                    .complete(true)
                    .build());
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

    // ---------------- QA ----------------

    private CaseInterrogationQA upsertQa(CaseInterrogation interrogation, Long qaId,
                                         String question, String answer) {
        String q = trimToNull(question);
        String a = trimToNull(answer);
        if (q == null && a == null) return null;

        if (interrogation.getQaList() == null) {
            interrogation.setQaList(new ArrayList<>());
        }
        List<CaseInterrogationQA> list = interrogation.getQaList();

        // 1. явный qaId с фронта
        Optional<CaseInterrogationQA> existing = qaId == null ? Optional.empty()
                : list.stream().filter(x -> qaId.equals(x.getId())).findFirst();

        if (qaId != null && existing.isEmpty()) {
            throw new NotFoundException(NotFoundMessage.QA.localized(currentLang(), qaId.toString()));
        }

        // 2. без qaId: тот же вопрос, у которого ещё нет ответа или ответ совпадает
        if (existing.isEmpty() && q != null) {
            existing = list.stream()
                    .filter(x -> q.equals(trimToNull(x.getQuestion())))
                    .filter(x -> x.getAnswer() == null || Objects.equals(trimToNull(x.getAnswer()), a))
                    .reduce((first, second) -> second); // самая поздняя
        }

        if (existing.isPresent()) {
            CaseInterrogationQA qa = existing.get();
            if (qa.getStatus() == QAStatusEnum.TRANSCRIBING) {
                log.warn("QA {} is transcribing, chat answer not applied", qa.getId());
                return qa;
            }
            if (q != null && !q.equals(trimToNull(qa.getQuestion()))) {
                qa.setQuestion(q);
            }
            if (a != null && !a.equals(trimToNull(qa.getAnswer()))) {
                qa.setAnswer(a);
                qa.setManuallyEdited(true);
                qa.setStatus(QAStatusEnum.TRANSCRIBED);
            }
            log.info("QA {} matched from chat for interrogation {}", qa.getId(), interrogation.getId());
            return qa;
        }

        // 3. ручной ввод: новая запись
        CaseInterrogationQA qa = CaseInterrogationQA.builder()
                .question(q)
                .answer(a)
                .status(a != null ? QAStatusEnum.TRANSCRIBED : QAStatusEnum.PENDING)
                .orderIndex(nextOrderIndex(list))
                .isEdited(false)
                .manuallyEdited(a != null)
                .isReformulated(false)
                .createdAt(LocalDateTime.now())
                .interrogation(interrogation)
                .audioRecords(new ArrayList<>())
                .build();

        CaseInterrogationQA saved = caseInterrogationQARepository.saveAndFlush(qa);
        list.add(saved);
        log.info("Manual QA {} created from chat for interrogation {} (orderIndex={})",
                saved.getId(), interrogation.getId(), saved.getOrderIndex());
        return saved;
    }

    private int nextOrderIndex(List<CaseInterrogationQA> list) {
        return list.stream()
                .map(CaseInterrogationQA::getOrderIndex)
                .filter(Objects::nonNull)
                .max(Integer::compare)
                .map(i -> i + 1)
                .orElse(0);
    }

    private static String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public record PreparedInterrogation(
            Long chatId,
            Long placeholderId,
            Long userMessageId,
            Long qaId,
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