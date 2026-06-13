package com.chalo.controller;

import com.chalo.dto.AdventureForm;
import com.chalo.model.Adventure;
import com.chalo.model.AdventurePhoto;
import com.chalo.model.AdventureStatus;
import com.chalo.model.JoinRequest;
import com.chalo.model.JoinRequestStatus;
import com.chalo.model.NotificationType;
import com.chalo.model.Tag;
import com.chalo.model.User;
import com.chalo.repository.AdventureRepository;
import com.chalo.repository.JoinRequestRepository;
import com.chalo.repository.TagRepository;
import com.chalo.repository.UserRepository;
import com.chalo.security.CustomUserDetails;
import com.chalo.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AdventureController {

    private final TagRepository         tagRepository;
    private final AdventureRepository   adventureRepository;
    private final UserRepository        userRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final NotificationService   notificationService;

    // ── Host Adventure — show form ─────────────────────────────────────────────
    // GET /adventures/new is restricted to ROLE_USER in SecurityConfig.

    @GetMapping("/adventures/new")
    public String showCreateForm(@AuthenticationPrincipal CustomUserDetails principal,
                                 Model model) {

        if (!model.containsAttribute("form")) {
            AdventureForm form = new AdventureForm();
            form.getPhotoUrls().add("");   // render one empty photo URL row
            model.addAttribute("form", form);
        }
        addReferenceData(model, principal);
        return "adventures/new";
    }

    // ── Host Adventure — create ────────────────────────────────────────────────
    // POST /adventures falls under anyRequest().hasRole("USER"); CSRF is injected
    // by th:action. One repository.save() persists the adventure plus its cascaded
    // photos and tag join rows.

    @PostMapping("/adventures")
    @Transactional
    public String createAdventure(@Valid @ModelAttribute("form") AdventureForm form,
                                  BindingResult bindingResult,
                                  @AuthenticationPrincipal CustomUserDetails principal,
                                  Model model,
                                  RedirectAttributes redirectAttrs) {

        if (bindingResult.hasErrors()) {
            addReferenceData(model, principal);
            return "adventures/new";
        }

        // Managed host reference without an extra SELECT (only the FK is needed)
        User host = userRepository.getReferenceById(principal.getId());

        Adventure adventure = Adventure.builder()
                .host(host)
                .title(form.getTitle().trim())
                .description(form.getDescription().trim())
                .locationName(form.getLocation().trim())
                .adventureDate(form.getAdventureDate())
                .maxParticipants(form.getAvailableSlots())
                .status(AdventureStatus.PUBLISHED)   // goes live + discoverable
                .build();

        // Attach selected interests
        List<Tag> tags = tagRepository.findByIdIn(form.getTagIds());
        adventure.getTags().addAll(tags);

        // Attach photos (drop blank rows); first valid URL becomes the cover image
        List<String> urls = form.getPhotoUrls().stream()
                .filter(u -> u != null && !u.isBlank())
                .map(String::trim)
                .toList();

        int order = 0;
        for (String url : urls) {
            adventure.getPhotos().add(
                    AdventurePhoto.builder()
                            .adventure(adventure)
                            .url(url)
                            .uploadedBy(host)
                            .displayOrder(order++)
                            .build());
        }
        if (!urls.isEmpty()) {
            adventure.setCoverImageUrl(urls.get(0));
        }

        adventureRepository.save(adventure);
        redirectAttrs.addFlashAttribute("createSuccess", "Your adventure is live and ready for explorers to join!");
        return "redirect:/my-adventures";
    }

    // ── Submit Join Request ────────────────────────────────────────────────────
    // POST /adventures/{id}/join — requires ROLE_USER (SecurityConfig).
    // All guard checks run before persisting so the DB unique constraint is a
    // final safety net, not the primary validation.

    @PostMapping("/adventures/{id}/join")
    @Transactional
    public String submitJoin(@PathVariable Long id,
                              @RequestParam(required = false) String message,
                              @AuthenticationPrincipal CustomUserDetails principal,
                              RedirectAttributes redirectAttrs) {

        Adventure adventure = adventureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (adventure.getStatus() != AdventureStatus.PUBLISHED) {
            redirectAttrs.addFlashAttribute("joinError", "This adventure is not accepting requests.");
            return "redirect:/adventures/" + id;
        }

        // Lazy proxy FK read — no extra SELECT needed
        if (adventure.getHost().getId().equals(principal.getId())) {
            redirectAttrs.addFlashAttribute("joinError", "You cannot join your own adventure.");
            return "redirect:/adventures/" + id;
        }

        User requester = userRepository.getReferenceById(principal.getId());

        if (joinRequestRepository.existsByAdventureAndRequester(adventure, requester)) {
            redirectAttrs.addFlashAttribute("joinError", "You have already sent a request for this adventure.");
            return "redirect:/adventures/" + id;
        }

        long accepted = joinRequestRepository.countByAdventureAndStatus(adventure, JoinRequestStatus.ACCEPTED);
        if (accepted >= adventure.getMaxParticipants()) {
            redirectAttrs.addFlashAttribute("joinError", "This adventure is full.");
            return "redirect:/adventures/" + id;
        }

        joinRequestRepository.save(
                JoinRequest.builder()
                        .adventure(adventure)
                        .requester(requester)
                        .message(message != null && !message.isBlank() ? message.trim() : null)
                        .build());

        redirectAttrs.addFlashAttribute("joinSuccess", true);
        return "redirect:/adventures/" + id;
    }

    // ── View Join Requests (host only) ────────────────────────────────────────
    // GET /adventures/{id}/requests — requires ROLE_USER (SecurityConfig).
    // Returns 403 if the logged-in user is not the adventure's host.

    @GetMapping("/adventures/{id}/requests")
    @Transactional(readOnly = true)
    public String showRequests(@PathVariable Long id,
                                @AuthenticationPrincipal CustomUserDetails principal,
                                Model model) {

        // findWithDetailById eagerly loads host via @EntityGraph
        Adventure adventure = adventureRepository.findWithDetailById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!adventure.getHost().getId().equals(principal.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        List<JoinRequest> requests = joinRequestRepository.findByAdventureWithRequester(adventure);
        long acceptedCount = joinRequestRepository.countByAdventureAndStatus(adventure, JoinRequestStatus.ACCEPTED);
        long pendingCount  = joinRequestRepository.countByAdventureAndStatus(adventure, JoinRequestStatus.PENDING);
        long slotsLeft     = Math.max(0, adventure.getMaxParticipants() - acceptedCount);

        model.addAttribute("adventure",     adventure);
        model.addAttribute("requests",      requests);
        model.addAttribute("acceptedCount", acceptedCount);
        model.addAttribute("pendingCount",  pendingCount);
        model.addAttribute("slotsLeft",     slotsLeft);
        model.addAttribute("userName",      principal.getName());

        return "adventures/requests";
    }

    // ── Cancel Adventure ──────────────────────────────────────────────────────
    // Only the host can cancel. Only PUBLISHED adventures can be cancelled —
    // CANCELLED is already terminal; DRAFT/COMPLETED are blocked.
    // All ACCEPTED participants receive an ADVENTURE_CANCELLED notification.
    // The adventure record and all join requests are preserved (soft cancel).

    @PostMapping("/adventures/{id}/cancel")
    @Transactional
    public String cancelAdventure(@PathVariable Long id,
                                   @AuthenticationPrincipal CustomUserDetails principal,
                                   RedirectAttributes redirectAttrs) {

        Adventure adventure = adventureRepository.findWithDetailById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!adventure.getHost().getId().equals(principal.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (adventure.getStatus() == AdventureStatus.CANCELLED) {
            redirectAttrs.addFlashAttribute("cancelError", "This adventure is already cancelled.");
            return "redirect:/adventures/" + id;
        }

        if (adventure.getStatus() != AdventureStatus.PUBLISHED) {
            redirectAttrs.addFlashAttribute("cancelError", "Only published adventures can be cancelled.");
            return "redirect:/adventures/" + id;
        }

        // Dirty-check: Hibernate emits the UPDATE on transaction commit
        adventure.setStatus(AdventureStatus.CANCELLED);

        // Notify every accepted participant in a single batch query
        List<JoinRequest> accepted = joinRequestRepository
                .findByAdventureWithRequester(adventure)
                .stream()
                .filter(jr -> jr.getStatus() == JoinRequestStatus.ACCEPTED)
                .toList();

        for (JoinRequest jr : accepted) {
            notificationService.notifyUser(
                    jr.getRequester().getId(),
                    "Adventure Cancelled",
                    "The host cancelled \"" + adventure.getTitle() + "\".",
                    NotificationType.ADVENTURE_CANCELLED,
                    "/adventures/" + id
            );
        }

        log.info("cancelAdventure: id={} host={} notified={} participants",
                id, principal.getId(), accepted.size());

        redirectAttrs.addFlashAttribute("cancelSuccess",
                "Adventure cancelled. " + accepted.size() + " participant(s) notified.");
        return "redirect:/my-adventures";
    }

    // ── Shared model data for the form view ────────────────────────────────────

    private void addReferenceData(Model model, CustomUserDetails principal) {
        model.addAttribute("allTags", tagRepository.findAllByOrderByNameAsc());
        model.addAttribute("today", LocalDate.now());   // date picker min
        model.addAttribute("userName", principal.getName());
    }
}
