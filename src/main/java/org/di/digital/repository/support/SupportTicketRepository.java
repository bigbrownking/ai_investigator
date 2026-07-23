package org.di.digital.repository.support;

import org.di.digital.model.support.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    @Modifying
    @Transactional
    @Query(value = "UPDATE support_tickets SET user_id = NULL WHERE user_id = :userId", nativeQuery = true)
    void clearTicketAuthor(@Param("userId") Long userId);
}