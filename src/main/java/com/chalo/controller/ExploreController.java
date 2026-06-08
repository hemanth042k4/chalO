package com.chalo.controller;

import com.chalo.model.Adventure;
import com.chalo.model.Tag;
import com.chalo.repository.AdventureRepository;
import com.chalo.repository.TagRepository;
import com.chalo.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ExploreController {

    private final AdventureRepository adventureRepository;
    private final TagRepository       tagRepository;

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

        model.addAttribute("adventures",     results);
        model.addAttribute("selectedTags",   selectedTags);   // resolved Tag objects
        model.addAttribute("searchTags",     hasTags ? tags : List.of()); // slugs for chip pre-check
        model.addAttribute("searchLocation", hasLocation ? location.trim() : "");
        model.addAttribute("allTags",        tagRepository.findAllByOrderByNameAsc());
        model.addAttribute("userName",       principal != null ? principal.getName() : null);

        return "explore";
    }
}
