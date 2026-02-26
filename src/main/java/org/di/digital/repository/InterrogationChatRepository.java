package org.di.digital.repository;

import org.di.digital.model.CaseInterrogationChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterrogationChatRepository extends JpaRepository<CaseInterrogationChat, Long> {
    Optional<CaseInterrogationChat> findByInterrogationId(Long interrogationId);
}
