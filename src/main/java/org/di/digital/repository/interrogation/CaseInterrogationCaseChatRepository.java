package org.di.digital.repository.interrogation;

import org.di.digital.model.interrogation.CaseInterrogationCaseChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CaseInterrogationCaseChatRepository extends JpaRepository<CaseInterrogationCaseChat, Long> {
    Optional<CaseInterrogationCaseChat> findByInterrogationIdAndUserId(Long interrogationId, Long userId);
}
