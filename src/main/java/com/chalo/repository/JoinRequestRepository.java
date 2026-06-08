package com.chalo.repository;

import com.chalo.model.Adventure;
import com.chalo.model.JoinRequest;
import com.chalo.model.JoinRequestStatus;
import com.chalo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {

    // ── Host: manage requests for their adventure ─────────────────────────────
    // Loads requester eagerly to avoid N+1 on the requests management page.
    @Query("""
            SELECT jr FROM JoinRequest jr
            JOIN FETCH jr.requester
            WHERE jr.adventure = :adventure
            ORDER BY jr.createdAt DESC
            """)
    List<JoinRequest> findByAdventureWithRequester(@Param("adventure") Adventure adventure);

    // ── Requester: duplicate-request guard ────────────────────────────────────
    Optional<JoinRequest> findByAdventureAndRequester(Adventure adventure, User requester);

    boolean existsByAdventureAndRequester(Adventure adventure, User requester);

    // ── Dashboard: my outgoing requests ───────────────────────────────────────
    // Loads adventure (title + date) eagerly for display.
    @Query("""
            SELECT jr FROM JoinRequest jr
            JOIN FETCH jr.adventure
            WHERE jr.requester = :requester
            ORDER BY jr.createdAt DESC
            """)
    List<JoinRequest> findByRequesterWithAdventure(@Param("requester") User requester);

    // ── Status filter ─────────────────────────────────────────────────────────
    List<JoinRequest> findByAdventureAndStatus(Adventure adventure, JoinRequestStatus status);

    long countByAdventureAndStatus(Adventure adventure, JoinRequestStatus status);

    // ── My Adventures: pending count for a batch of adventures (one query) ────
    // Returns [adventure_id, count] rows — adventures with zero pending requests
    // are absent. Caller converts to Map<Long,Long> and uses getOrDefault(id, 0).
    @Query("""
            SELECT jr.adventure.id, COUNT(jr)
            FROM JoinRequest jr
            WHERE jr.adventure.id IN :adventureIds
              AND jr.status = com.chalo.model.JoinRequestStatus.PENDING
            GROUP BY jr.adventure.id
            """)
    List<Object[]> countPendingByAdventureIds(@Param("adventureIds") List<Long> adventureIds);

    // ── Requester: "Adventures I've Joined" — ACCEPTED only ──────────────────
    // JOIN FETCH adventure + host in one query. Tags on adventure batch-load via
    // @BatchSize(20) during template render — no extra round-trips per card.
    @Query("""
            SELECT jr FROM JoinRequest jr
            JOIN FETCH jr.adventure a
            JOIN FETCH a.host
            WHERE jr.requester = :requester
              AND jr.status = com.chalo.model.JoinRequestStatus.ACCEPTED
            ORDER BY a.adventureDate ASC
            """)
    List<JoinRequest> findAcceptedByRequesterWithAdventureAndHost(@Param("requester") User requester);

    // ── Requester: "Pending Requests" — outgoing PENDING only ────────────────
    // Same JOIN FETCH pattern as above — host name needed for card display.
    @Query("""
            SELECT jr FROM JoinRequest jr
            JOIN FETCH jr.adventure a
            JOIN FETCH a.host
            WHERE jr.requester = :requester
              AND jr.status = com.chalo.model.JoinRequestStatus.PENDING
            ORDER BY jr.createdAt DESC
            """)
    List<JoinRequest> findPendingByRequesterWithAdventureAndHost(@Param("requester") User requester);

    // ── Batch accepted count per adventure (slots-left for joined cards) ──────
    // Returns [adventure_id, count] rows — absent entries mean zero accepted.
    @Query("""
            SELECT jr.adventure.id, COUNT(jr)
            FROM JoinRequest jr
            WHERE jr.adventure.id IN :adventureIds
              AND jr.status = com.chalo.model.JoinRequestStatus.ACCEPTED
            GROUP BY jr.adventure.id
            """)
    List<Object[]> countAcceptedByAdventureIds(@Param("adventureIds") List<Long> adventureIds);

    // ── Host statistics: total accepted participants across all hosted adventures
    @Query("""
            SELECT COUNT(jr) FROM JoinRequest jr
            WHERE jr.adventure.host.id = :hostId
              AND jr.status = com.chalo.model.JoinRequestStatus.ACCEPTED
            """)
    long countAcceptedByHostId(@Param("hostId") Long hostId);

    // ── Accept / Reject: one query, no lazy-load surprises ───────────────────
    @Query("""
            SELECT jr FROM JoinRequest jr
            JOIN FETCH jr.adventure a
            JOIN FETCH a.host
            WHERE jr.id = :id
            """)
    Optional<JoinRequest> findByIdWithAdventureAndHost(@Param("id") Long id);

    // ── Crew Chat authorization: accepted participant check ───────────────────
    // Used by CrewChatService.canAccess() — host check is a simple ID equality,
    // this covers the participant side without touching ChatParticipant.
    boolean existsByAdventureIdAndRequesterIdAndStatus(Long adventureId,
                                                       Long requesterId,
                                                       JoinRequestStatus status);

    // ── Admin ─────────────────────────────────────────────────────────────────
    List<JoinRequest> findByStatusOrderByCreatedAtDesc(JoinRequestStatus status);
}
