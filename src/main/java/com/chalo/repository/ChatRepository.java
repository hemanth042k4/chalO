package com.chalo.repository;

import com.chalo.model.Adventure;
import com.chalo.model.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    // ── Join request acceptance: find or create ───────────────────────────────
    Optional<Chat> findByAdventure(Adventure adventure);

    Optional<Chat> findByAdventureId(Long adventureId);

    // ── Chat list page: all chats the current user participates in ────────────
    // Fetches the adventure (for title) in the same query.
    @Query("""
            SELECT c FROM Chat c
            JOIN FETCH c.adventure a
            JOIN c.participants cp
            WHERE cp.user.id = :userId
            ORDER BY c.createdAt DESC
            """)
    List<Chat> findChatsForUser(@Param("userId") Long userId);

    // ── Authorization: verify user is a participant before allowing access ─────
    @Query("""
            SELECT COUNT(cp) > 0 FROM ChatParticipant cp
            WHERE cp.chat.id = :chatId
              AND cp.user.id = :userId
            """)
    boolean isParticipant(@Param("chatId") Long chatId, @Param("userId") Long userId);
}
