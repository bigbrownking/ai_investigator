package org.di.digital.repository.interrogation;

import org.di.digital.model.interrogation.CaseInterrogationContradiction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseInterrogationContradictionRepository
        extends JpaRepository<CaseInterrogationContradiction, Long> {

    List<CaseInterrogationContradiction> findByInterrogationChatIdOrderByIdAsc(Long chatId);

    List<CaseInterrogationContradiction> findBySourceMessageIdOrderByIdAsc(Long sourceMessageId);

    void deleteAllByInterrogationChatId(Long chatId);
}