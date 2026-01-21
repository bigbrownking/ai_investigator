package org.di.digital.repository;

import org.di.digital.model.CaseChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CaseChatMessageRepository extends JpaRepository<CaseChatMessage, Long> {
    @Query("SELECT m FROM CaseChatMessage m WHERE m.chat.id = :chatId ORDER BY m.createdDate ASC")
    Page<CaseChatMessage> findByChatId(@Param("chatId") Long chatId, Pageable pageable);

    @Query("SELECT m FROM CaseChatMessage m WHERE m.chat.id = :chatId ORDER BY m.createdDate DESC")
    List<CaseChatMessage> findRecentMessages(@Param("chatId") Long chatId, Pageable pageable);

    @Query("SELECT COUNT(m) FROM CaseChatMessage m WHERE m.chat.id = :chatId")
    long countByChatId(@Param("chatId") Long chatId);

    @Query("SELECT m FROM CaseChatMessage m WHERE m.chat.id = :chatId AND m.createdDate > :afterDate ORDER BY m.createdDate ASC")
    List<CaseChatMessage> findMessagesAfter(@Param("chatId") Long chatId, @Param("afterDate") LocalDateTime afterDate);

    @Query("DELETE FROM CaseChatMessage m WHERE m.chat.id = :chatId AND m.createdDate < :beforeDate")
    void deleteOldMessages(@Param("chatId") Long chatId, @Param("beforeDate") LocalDateTime beforeDate);
    @Modifying
    @Query("DELETE FROM CaseChatMessage m WHERE m.chat.id = :chatId")
    void deleteAllByChatId(@Param("chatId") Long chatId);
}