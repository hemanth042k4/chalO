package com.chalo.controller;

import com.chalo.model.Adventure;
import com.chalo.model.AdventureStatus;
import com.chalo.model.JoinRequest;
import com.chalo.model.User;
import com.chalo.repository.AdventureRepository;
import com.chalo.repository.JoinRequestRepository;
import com.chalo.repository.UserRepository;
import com.chalo.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class CrewHubController {

    private final AdventureRepository   adventureRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final UserRepository        userRepository;

    // ── GET /my-crews ─────────────────────────────────────────────────────────
    // Requires ROLE_USER (caught by the anyRequest catch-all in SecurityConfig).
    // Shows every crew the user belongs to — either as host or accepted member.

    @GetMapping("/my-crews")
    public String myCrews(@AuthenticationPrincipal CustomUserDetails principal,
                          Model model) {

        User user = userRepository.getReferenceById(principal.getId());

        // ── Hosted crews ──────────────────────────────────────────────────────
        // All adventures the user hosts, excluding CANCELLED ones.
        // PUBLISHED, COMPLETED, and DRAFT are included so the host can always
        // reach their crew chat regardless of adventure lifecycle stage.
        List<Adventure> hostedCrews = adventureRepository
                .findByHostOrderByAdventureDateDesc(user)
                .stream()
                .filter(a -> a.getStatus() != AdventureStatus.CANCELLED)
                .toList();

        // ── Member crews ──────────────────────────────────────────────────────
        // Adventures where the user has an ACCEPTED join request.
        // The existing query already JOIN FETCHes adventure + host in one round-trip
        // and returns only ACCEPTED rows — no N+1, no extra fetches.
        // CANCELLED adventures are filtered here in Java because the repository
        // query has no status guard; cancelled crews should not be surfaced.
        List<JoinRequest> memberCrews = joinRequestRepository
                .findAcceptedByRequesterWithAdventureAndHost(user)
                .stream()
                .filter(jr -> jr.getAdventure().getStatus() != AdventureStatus.CANCELLED)
                .toList();

        // ── Deduplication note ────────────────────────────────────────────────
        // A user can never have a join request for their own adventure: the join
        // flow rejects it ("You cannot join your own adventure.").  The two lists
        // are therefore always disjoint — no explicit de-dup step is needed.

        // ── Batch accepted-participant counts (one query for all cards) ────────
        // Gathers adventure IDs from both sections into a single list so only
        // one SQL round-trip is needed regardless of page size.
        List<Long> allIds = new ArrayList<>();
        hostedCrews.forEach(a -> allIds.add(a.getId()));
        memberCrews.forEach(jr -> allIds.add(jr.getAdventure().getId()));

        Map<Long, Long> memberCounts = Collections.emptyMap();
        if (!allIds.isEmpty()) {
            List<Object[]> rows = joinRequestRepository.countAcceptedByAdventureIds(allIds);
            memberCounts = rows.stream().collect(Collectors.toMap(
                    row -> (Long) row[0],
                    row -> (Long) row[1]
            ));
        }

        model.addAttribute("hostedCrews",  hostedCrews);
        model.addAttribute("memberCrews",  memberCrews);
        model.addAttribute("memberCounts", memberCounts);
        model.addAttribute("totalCrews",   hostedCrews.size() + memberCrews.size());
        model.addAttribute("userName",     principal.getName());

        return "my-crews";
    }
}
