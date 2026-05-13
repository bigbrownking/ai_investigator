package org.di.digital.repository.interrogation;

import org.di.digital.model.interrogation.CaseInterrogationChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CaseInterrogationChatRepository extends JpaRepository<CaseInterrogationChat, Long> {
    Optional<CaseInterrogationChat> findByInterrogationId(Long interrogationId);
}
