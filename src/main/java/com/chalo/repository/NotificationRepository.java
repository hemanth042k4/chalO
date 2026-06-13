package com.chalo.repository;

import com.chalo.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // ── Fetch all for a user, newest first ────────────────────────────────────
    @Query("""
            SELECT n FROM Notification n
            WHERE n.user.id = :userId
            ORDER BY n.createdAt DESC
            """)
    List<Notification> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    // ── Unread count — drives the header badge ────────────────────────────────
    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.user.id = :userId AND n.read = false
            """)
    long countUnreadByUserId(@Param("userId") Long userId);

    // ── Single notification with ownership check (no lazy user load needed) ──
    @Query("""
            SELECT n FROM Notification n
            WHERE n.id = :id AND n.user.id = :userId
            """)
    Optional<Notification> findByIdAndUserId(@Param("id") Long id,
                                             @Param("userId") Long userId);

    // ── Bulk mark-all-read ────────────────────────────────────────────────────
    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.read = true
            WHERE n.user.id = :userId AND n.read = false
            """)
    void markAllReadByUserId(@Param("userId") Long userId);
}
