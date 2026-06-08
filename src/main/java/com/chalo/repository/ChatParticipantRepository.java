package com.chalo.repository;

import com.chalo.model.Chat;
import com.chalo.model.ChatParticipant;
import com.chalo.model.ChatParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, ChatParticipantId> {

    // ── Duplicate-add guard ───────────────────────────────────────────────────
    boolean existsByIdChatIdAndIdUserId(Long chatId, Long userId);

    // ── Chat room: show participant list with user names ──────────────────────
    @Query("""
            SELECT cp FROM ChatParticipant cp
            JOIN FETCH cp.user
            WHERE cp.chat = :chat
            """)
    List<ChatParticipant> findByChatWithUser(@Param("chat") Chat chat);
}
