package com.chalo.controller;

import com.chalo.model.Adventure;
import com.chalo.model.Tag;
import com.chalo.repository.AdventureRepository;
import com.chalo.repository.JoinRequestRepository;
import com.chalo.repository.TagRepository;
import com.chalo.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ExploreController {

    private final AdventureRepository   adventureRepository;
    private final TagRepository         tagRepository;
    private final JoinRequestRepository joinRequestRepository;

    // ── Explore Adventures — GET /explore ─────────────────────────────────────
    // Permitted for all users (including anonymous) per SecurityConfig.
    // Params arrive from the dashboard search form: ?tags=slug&tags=slug&location=...

    @GetMapping("/explore")
    public String explore(
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) String location,
            @AuthenticationPrincipal CustomUserDetails principal,
            Model model) {

        boolean hasTags     = tags != null && !tags.isEmpty();
        boolean hasLocation = location != null && !location.isBlank();
        String  loc         = hasLocation ? location.trim() : null;

        // -1L never matches a real auto-increment PK — anonymous users see
        // all qualifying adventures; logged-in users skip their own.
        Long excludeHostId = (principal != null) ? principal.getId() : -1L;

        List<Tag>       selectedTags;
        List<Adventure> results;

        if (hasTags) {
            // Resolve slug params → Tag entities → IDs for the JPQL IN clause
            selectedTags = tagRepository.findBySlugIn(tags);
            List<Long> tagIds = selectedTags.stream().map(Tag::getId).toList();
            results = tagIds.isEmpty()
                    ? List.of()
                    : adventureRepository.search(tagIds, loc, excludeHostId);
        } else {
            // No interest filter — show all upcoming published (optional location)
            selectedTags = List.of();
            results      = adventureRepository.findUpcomingPublished(loc, excludeHostId);
        }

        // Batch accepted counts — one query for all result IDs, no N+1.
        // Converts to Map<Long, Integer> (slotsLeft per adventure) for clean template comparisons.
        List<Long> resultIds = results.stream().map(Adventure::getId).toList();
        Map<Long, Integer> slotsLeftMap = Collections.emptyMap();
        if (!resultIds.isEmpty()) {
            Map<Long, Long> acceptedCounts = new HashMap<>();
            joinRequestRepository.countAcceptedByAdventureIds(resultIds)
                    .forEach(row -> acceptedCounts.put((Long) row[0], (Long) row[1]));
            slotsLeftMap = new HashMap<>();
            for (Adventure adv : results) {
                long accepted = acceptedCounts.getOrDefault(adv.getId(), 0L);
                slotsLeftMap.put(adv.getId(),
                        (int) Math.max(0, adv.getMaxParticipants() - accepted));
            }
        }

        model.addAttribute("adventures",     results);
        model.addAttribute("slotsLeftMap",   slotsLeftMap);
        model.addAttribute("selectedTags",   selectedTags);   // resolved Tag objects
        model.addAttribute("searchTags",     hasTags ? tags : List.of()); // slugs for chip pre-check
        model.addAttribute("searchLocation", hasLocation ? location.trim() : "");
        model.addAttribute("allTags",        tagRepository.findAllByOrderByNameAsc());
        model.addAttribute("userName",       principal != null ? principal.getName() : null);

        return "explore";
    }
}
