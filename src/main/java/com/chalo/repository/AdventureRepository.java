package com.chalo.repository;

import com.chalo.model.Adventure;
import com.chalo.model.AdventureStatus;
import com.chalo.model.Tag;
import com.chalo.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AdventureRepository extends JpaRepository<Adventure, Long> {

    // ── Detail page ──────────────────────────────────────────────────────────
    // Fetches tags, photos, and host in one query to avoid lazy-load round trips
    // on the detail page template.
    @EntityGraph(attributePaths = {"tags", "photos", "host"})
    Optional<Adventure> findWithDetailById(Long id);

    // ── Explore / Search — tag-filtered ──────────────────────────────────────
    // Returns DISTINCT published upcoming adventures whose tag set intersects
    // the selected tag IDs. Location is an optional case-insensitive substring
    // match. Excludes adventures hosted by excludeHostId (pass -1 for guests).
    // @BatchSize(size=20) on Adventure.tags batches the lazy tag loads.
    @Query("""
            SELECT DISTINCT a FROM Adventure a
            JOIN a.tags t
            WHERE t.id IN :tagIds
              AND (:location IS NULL
                   OR LOWER(a.locationName) LIKE LOWER(CONCAT('%', :location, '%')))
              AND a.status = com.chalo.model.AdventureStatus.PUBLISHED
              AND a.adventureDate >= CURRENT_DATE
              AND a.host.id <> :excludeHostId
            ORDER BY a.adventureDate ASC
            """)
    List<Adventure> search(@Param("tagIds") List<Long> tagIds,
                           @Param("location") String location,
                           @Param("excludeHostId") Long excludeHostId);

    // ── Explore / Search — no tags (show all or location-only) ───────────────
    // Used when the user submits the search form with no interest chips checked.
    // Location filter and host exclusion work the same way as search().
    @Query("""
            SELECT a FROM Adventure a
            WHERE (:location IS NULL
                   OR LOWER(a.locationName) LIKE LOWER(CONCAT('%', :location, '%')))
              AND a.status = com.chalo.model.AdventureStatus.PUBLISHED
              AND a.adventureDate >= CURRENT_DATE
              AND a.host.id <> :excludeHostId
            ORDER BY a.adventureDate ASC
            """)
    List<Adventure> findUpcomingPublished(@Param("location") String location,
                                          @Param("excludeHostId") Long excludeHostId);

    // ── Recommended For You (Dashboard) ──────────────────────────────────────
    // Returns published adventures ordered by the number of tags that overlap
    // with the current user's interests. Excludes adventures the user hosts.
    // Caller must ensure interests is non-empty before calling (empty IN clause
    // returns no results by design).
    @Query("""
            SELECT a FROM Adventure a
            JOIN a.tags t
            WHERE t IN :interests
              AND a.status = com.chalo.model.AdventureStatus.PUBLISHED
              AND a.adventureDate >= CURRENT_DATE
              AND a.host.id <> :userId
            GROUP BY a
            ORDER BY COUNT(t) DESC
            """)
    List<Adventure> findRecommended(@Param("interests") Set<Tag> interests,
                                    @Param("userId") Long userId,
                                    Pageable pageable);

    // ── Dashboard — my hosted adventures ─────────────────────────────────────
    List<Adventure> findByHostOrderByAdventureDateDesc(User host);

    // ── Host profile card on detail page (3 most recent) ─────────────────────
    @Query("""
            SELECT a FROM Adventure a
            WHERE a.host.id = :hostId
              AND a.status IN (
                  com.chalo.model.AdventureStatus.PUBLISHED,
                  com.chalo.model.AdventureStatus.COMPLETED)
            ORDER BY a.adventureDate DESC
            """)
    List<Adventure> findPublishedByHostId(@Param("hostId") Long hostId, Pageable pageable);

    // ── Host statistics ───────────────────────────────────────────────────────
    long countByHost(User host);
    long countByHostAndStatus(User host, AdventureStatus status);

    // ── Admin ─────────────────────────────────────────────────────────────────
    List<Adventure> findByStatusOrderByCreatedAtDesc(AdventureStatus status);
}
