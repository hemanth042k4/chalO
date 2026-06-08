package com.chalo.service;

import com.chalo.model.Adventure;
import com.chalo.model.Chat;
import com.chalo.model.JoinRequestStatus;
import com.chalo.model.Message;
import com.chalo.model.User;
import com.chalo.repository.AdventureRepository;
import com.chalo.repository.ChatRepository;
import com.chalo.repository.JoinRequestRepository;
import com.chalo.repository.MessageRepository;
import com.chalo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrewChatService {

    private final ChatRepository        chatRepository;
    private final MessageRepository     messageRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final AdventureRepository   adventureRepository;
    private final UserRepository        userRepository;

    // ── Authorization ─────────────────────────────────────────────────────────
    // Host check is a simple ID comparison (no DB hit; host is eagerly loaded
    // by findWithDetailById before this is called).
    // Participant check queries JoinRequest directly — ChatParticipant is unused
    // for authorization in MVP.

    public boolean canAccess(Adventure adventure, Long userId) {
        if (adventure.getHost().getId().equals(userId)) {
            return true;
        }
        return joinRequestRepository.existsByAdventureIdAndRequesterIdAndStatus(
                adventure.getId(), userId, JoinRequestStatus.ACCEPTED);
    }

    // ── Chat creation ─────────────────────────────────────────────────────────
    // Lazy: created on first access, not on join-request acceptance.
    // getReferenceById gives a proxy with only the PK set — sufficient for the FK.

    @Transactional
    public Chat getOrCreateChat(Long adventureId) {
        return chatRepository.findByAdventureId(adventureId)
                .orElseGet(() -> {
                    log.debug("getOrCreateChat → no chat yet, creating for adventure {}", adventureId);
                    Adventure ref = adventureRepository.getReferenceById(adventureId);
                    return chatRepository.save(Chat.builder().adventure(ref).build());
                });
    }

    // ── Message persistence ───────────────────────────────────────────────────
    // Content is trimmed and capped server-side; the @NotBlank constraint on
    // Message.content means a blank string would fail validation before save.

    @Transactional
    public void postMessage(Long chatId, Long senderId, String content) {
        String trimmed = content.trim();
        if (trimmed.length() > 2000) {
            trimmed = trimmed.substring(0, 2000);
        }
        Chat chatRef   = chatRepository.getReferenceById(chatId);
        User senderRef = userRepository.getReferenceById(senderId);
        messageRepository.save(Message.builder()
                .chat(chatRef)
                .sender(senderRef)
                .content(trimmed)
                .build());
        log.debug("postMessage → chatId={} senderId={} chars={}", chatId, senderId, trimmed.length());
    }
}
