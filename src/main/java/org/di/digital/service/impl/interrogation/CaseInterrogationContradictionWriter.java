package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.interrogation.ContradictionResponse;
import org.di.digital.model.interrogation.CaseInterrogationChat;
import org.di.digital.model.interrogation.CaseInterrogationContradiction;
import org.di.digital.repository.interrogation.CaseInterrogationChatRepository;
import org.di.digital.repository.interrogation.CaseInterrogationContradictionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseInterrogationContradictionWriter {

    private final CaseInterrogationContradictionRepository contradictionRepository;
    private final CaseInterrogationChatRepository chatRepository;

    @Transactional
    public List<CaseInterrogationContradiction> save(Long chatId, Long sourceMessageId,
                                                     String indication,
                                                     List<ContradictionResponse.ContradictionItem> items) {
        if (items == null || items.isEmpty()) return List.of();

        CaseInterrogationChat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("Чат не найден: " + chatId));

        List<CaseInterrogationContradiction> entities = items.stream()
                .map(i -> CaseInterrogationContradiction.builder()
                        .interrogationChat(chat)
                        .sourceMessageId(sourceMessageId)
                        .indication(indication)
                        .text(i.getText())
                        .confidencePercent(i.getConfidencePercent())
                        .references(i.getReferences())
                        .build())
                .toList();

        return contradictionRepository.saveAll(entities);
    }
}