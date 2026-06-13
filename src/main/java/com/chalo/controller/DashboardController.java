package com.chalo.controller;

import com.chalo.repository.TagRepository;
import com.chalo.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final TagRepository tagRepository;

    // Sample destinations used to seed the location autocomplete datalist.
    // Replaced later by live OpenStreetMap Nominatim results via /api/locations/suggest.
    private static final List<String> LOCATION_SUGGESTIONS = List.of(
            "Kudremukh", "Kodachadri", "Kumara Parvatha", "Gokarna", "Coorg");

    // ── Dashboard ────────────────────────────────────────────────────────────
    // Landing page after login / registration (SecurityConfig defaultSuccessUrl
    // and AuthenticationController both redirect here). Requires ROLE_USER.

    @GetMapping("/")
    public String root(@AuthenticationPrincipal CustomUserDetails user) {
        return (user != null) ? "redirect:/dashboard" : "redirect:/login";
    }

    @GetMapping("/dashboard")
    public String showDashboard(@AuthenticationPrincipal CustomUserDetails user,
                                Model model) {

        // Welcome header — logged-in user's display name
        model.addAttribute("userName", user.getName());

        // Interest chips are data-driven from the tags table so each chip carries
        // a real tag slug. The search form posts these slugs to /explore, giving
        // the future Adventure Search its tag filters for free.
        model.addAttribute("allTags", tagRepository.findAllByOrderByNameAsc());

        // Location autocomplete source (client-side datalist for now)
        model.addAttribute("locationSuggestions", LOCATION_SUGGESTIONS);

        // Placeholder adventure cards. Same shape future Adventure entities expose,
        // so swapping these for live data is a one-line change in the service layer.
        model.addAttribute("recommendedAdventures", placeholderAdventures());
        model.addAttribute("recentAdventures", placeholderAdventures());

        return "dashboard";
    }

    // ── Placeholder data ─────────────────────────────────────────────────────
    // Keys mirror the fields a real adventure card will bind to:
    // coverImageUrl, title, locationName, hostName, date, tags.

    private List<Map<String, Object>> placeholderAdventures() {
        return List.of(
                Map.of(
                        "coverImageUrl",  "https://images.unsplash.com/photo-1551632811-561732d1e306?auto=format&fit=crop&w=600&q=70",
                        "title",          "Sunrise Trek to Kumara Parvatha",
                        "locationName",   "Kukke Subramanya, Karnataka",
                        "hostName",       "Aarav Shetty",
                        "date",           "Sat, 14 Jun 2026",
                        "tags",           List.of("Trekking", "Sunrise"),
                        "spotsLeft",      3,
                        "maxParticipants", 8,
                        "isFull",         false
                ),
                Map.of(
                        "coverImageUrl",  "https://images.unsplash.com/photo-1504280390367-361c6d9f38f4?auto=format&fit=crop&w=600&q=70",
                        "title",          "Overnight Camping at Kodachadri",
                        "locationName",   "Kodachadri, Karnataka",
                        "hostName",       "Meera Nair",
                        "date",           "Fri, 20 Jun 2026",
                        "tags",           List.of("Camping", "Photography"),
                        "spotsLeft",      0,
                        "maxParticipants", 6,
                        "isFull",         true
                ),
                Map.of(
                        "coverImageUrl",  "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=600&q=70",
                        "title",          "Beach Hopping in Gokarna",
                        "locationName",   "Gokarna, Karnataka",
                        "hostName",       "Rohan Kamath",
                        "date",           "Sun, 28 Jun 2026",
                        "tags",           List.of("Beach", "Road Trip"),
                        "spotsLeft",      2,
                        "maxParticipants", 10,
                        "isFull",         false
                )
        );
    }
}
