package com.chalo.service;

import com.chalo.model.Notification;
import com.chalo.model.NotificationType;
import com.chalo.model.User;
import com.chalo.repository.NotificationRepository;
import com.chalo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository         userRepository;

    // ── Create ────────────────────────────────────────────────────────────────
    // Accepts userId (not a User entity) to avoid forcing callers to load the
    // full entity — getReferenceById gives a proxy sufficient for the FK column.

    @Transactional
    public void notifyUser(Long userId, String title, String message,
                           NotificationType type, String link) {
        User userRef = userRepository.getReferenceById(userId);
        notificationRepository.save(
                Notification.builder()
                        .user(userRef)
                        .title(title)
                        .message(message)
                        .type(type)
                        .link(link)
                        .build()
        );
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Notification> getAllForUser(Long userId) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadCountForUser(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    // Marks the notification as read and returns its link (for click-to-navigate).
    // Combines both operations in one transaction to avoid a second round-trip.
    @Transactional
    public String markAsReadAndGetLink(Long notificationId, Long userId) {
        Notification n = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        n.setRead(true);
        return n.getLink();
    }

    // ── Time-ago helper ───────────────────────────────────────────────────────
    // Static so the NotificationController can reference it without circular dep.
    // Uses JVM local time — consistent with @CreationTimestamp which also uses
    // the JVM clock.

    public static String timeAgo(LocalDateTime dt) {
        long secs = ChronoUnit.SECONDS.between(dt, LocalDateTime.now());
        if (secs < 60)  return "just now";
        long mins = secs / 60;
        if (mins < 60)  return mins + "m ago";
        long hours = mins / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 7)   return days + "d ago";
        long weeks = days / 7;
        if (weeks < 5)  return weeks + "w ago";
        return ChronoUnit.MONTHS.between(dt, LocalDateTime.now()) + "mo ago";
    }
}
