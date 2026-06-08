package com.chalo.repository;

import com.chalo.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // ── Chat room: initial full load ──────────────────────────────────────────
    // JOIN FETCH sender prevents N+1 when rendering each message's sender name.
    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.sender
            WHERE m.chat.id = :chatId
            ORDER BY m.createdAt ASC
            """)
    List<Message> findByChatIdWithSender(@Param("chatId") Long chatId);

    // ── Chat polling endpoint ─────────────────────────────────────────────────
    // JS fetch() calls GET /api/chats/{id}/messages?after={lastMessageId}
    // every 5 seconds. Returns only rows newer than the last seen ID,
    // keeping the payload minimal.
    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.sender
            WHERE m.chat.id = :chatId
              AND m.id > :afterId
            ORDER BY m.createdAt ASC
            """)
    List<Message> findNewMessages(@Param("chatId") Long chatId,
                                  @Param("afterId") Long afterId);
}
