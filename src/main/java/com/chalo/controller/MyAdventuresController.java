package com.chalo.controller;

import com.chalo.model.Adventure;
import com.chalo.model.JoinRequest;
import com.chalo.model.User;
import com.chalo.repository.AdventureRepository;
import com.chalo.repository.JoinRequestRepository;
import com.chalo.repository.UserRepository;
import com.chalo.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MyAdventuresController {

    private final AdventureRepository   adventureRepository;
    private final UserRepository        userRepository;
    private final JoinRequestRepository joinRequestRepository;

    @GetMapping("/my-adventures")
    public String myAdventures(@AuthenticationPrincipal CustomUserDetails principal,
                               Model model) {

        User host = userRepository.getReferenceById(principal.getId());

        List<Adventure> hosted = adventureRepository.findByHostOrderByAdventureDateDesc(host);

        List<Long> adventureIds = hosted.stream().map(Adventure::getId).toList();
        log.debug("myAdventures → hostId={} adventureIds={}", principal.getId(), adventureIds);

        Map<Long, Long> pendingCounts = Collections.emptyMap();
        if (!adventureIds.isEmpty()) {
            List<Object[]> rows = joinRequestRepository.countPendingByAdventureIds(adventureIds);
            log.debug("myAdventures → countPendingByAdventureIds raw rows={}", rows.size());
            for (Object[] row : rows) {
                log.debug("  adventureId={} pendingCount={}", row[0], row[1]);
            }

            pendingCounts = rows.stream()
                    .collect(Collectors.toMap(
                            row -> (Long) row[0],
                            row -> (Long) row[1]
                    ));
        }

        log.debug("myAdventures → pendingCounts map={}", pendingCounts);

        // ── Requester sections (adventures current user joined / applied to) ──
        User requester = host; // same User object — host is just a proxy reference
        List<JoinRequest> joinedRequests  = joinRequestRepository
                .findAcceptedByRequesterWithAdventureAndHost(requester);
        List<JoinRequest> pendingRequests = joinRequestRepository
                .findPendingByRequesterWithAdventureAndHost(requester);

        // TEMP DEBUG — remove after confirming root cause (badge vs card mismatch)
        log.debug("joinedRequests.size()={}", joinedRequests.size());
        for (JoinRequest jr : joinedRequests) {
            log.debug("  jr.id={} adventure.id={} adventure.title={}",
                    jr.getId(), jr.getAdventure().getId(), jr.getAdventure().getTitle());
        }

        // Accepted count per joined adventure (one batch query, no N+1).
        // Template computes slotsLeft = adventure.maxParticipants - acceptedCount.
        Map<Long, Long> joinedAcceptedCounts = Collections.emptyMap();
        if (!joinedRequests.isEmpty()) {
            List<Long> joinedAdventureIds = joinedRequests.stream()
                    .map(jr -> jr.getAdventure().getId()).toList();
            List<Object[]> acceptedRows = joinRequestRepository
                    .countAcceptedByAdventureIds(joinedAdventureIds);
            joinedAcceptedCounts = acceptedRows.stream().collect(Collectors.toMap(
                    row -> (Long) row[0],
                    row -> (Long) row[1]
            ));
        }

        model.addAttribute("hostedAdventures",    hosted);
        model.addAttribute("pendingCounts",        pendingCounts);
        model.addAttribute("joinedRequests",       joinedRequests);
        model.addAttribute("joinedAcceptedCounts", joinedAcceptedCounts);
        model.addAttribute("pendingRequests",      pendingRequests);
        model.addAttribute("userName",             principal.getName());

        return "my-adventures";
    }
}
