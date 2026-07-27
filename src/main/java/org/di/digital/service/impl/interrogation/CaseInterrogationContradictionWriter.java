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
        if (items == null || items.isEmpty()) {
            log.debug("Nothing to save: empty contradictions for chatId={}, sourceMessageId={}",
                    chatId, sourceMessageId);
            return List.of();
        }

        CaseInterrogationChat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("Чат не найден: " + chatId));

        log.debug("Saving {} contradictions for chatId={}, sourceMessageId={}, indication='{}'",
                items.size(), chatId, sourceMessageId, truncate(indication, 100));

        List<CaseInterrogationContradiction> entities = items.stream()
                .map(i -> {
                    log.debug("  contradiction: confidence={}%, refs={}, text='{}'",
                            i.getConfidencePercent(),
                            i.getReferences() == null ? 0 : i.getReferences().size(),
                            truncate(i.getText(), 120));
                    return CaseInterrogationContradiction.builder()
                            .interrogationChat(chat)
                            .sourceMessageId(sourceMessageId)
                            .indication(indication)
                            .text(i.getText())
                            .confidencePercent(i.getConfidencePercent())
                            .references(i.getReferences())
                            .build();
                })
                .toList();

        List<CaseInterrogationContradiction> saved = contradictionRepository.saveAll(entities);

        log.info("Saved {} contradictions for chatId={}, sourceMessageId={}, ids={}",
                saved.size(), chatId, sourceMessageId,
                saved.stream().map(CaseInterrogationContradiction::getId).toList());

        return saved;
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}