package com.chalo.controller;

import com.chalo.model.Adventure;
import com.chalo.model.AdventureStatus;
import com.chalo.model.JoinRequest;
import com.chalo.model.JoinRequestStatus;
import com.chalo.model.NotificationType;
import com.chalo.model.User;
import com.chalo.repository.AdventureRepository;
import com.chalo.repository.JoinRequestRepository;
import com.chalo.repository.UserRepository;
import com.chalo.security.CustomUserDetails;
import com.chalo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AdventureDetailsController {

    private final AdventureRepository   adventureRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final UserRepository        userRepository;
    private final NotificationService   notificationService;

    // ── Adventure Detail — public ─────────────────────────────────────────────

    @GetMapping("/adventures/{id}")
    public String showDetail(@PathVariable Long id,
                             @AuthenticationPrincipal CustomUserDetails principal,
                             Model model) {

        Adventure adventure = adventureRepository.findWithDetailById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        User    host    = adventure.getHost();
        boolean isOwner = (principal != null) && principal.getId().equals(host.getId());

        // CANCELLED adventures stay visible so notification links (/adventures/{id})
        // remain reachable by participants. Only DRAFT/COMPLETED are hidden from
        // non-owners.
        if (adventure.getStatus() != AdventureStatus.PUBLISHED
                && adventure.getStatus() != AdventureStatus.CANCELLED
                && !isOwner) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        long totalHosted    = adventureRepository.countByHost(host);
        long totalCompleted = adventureRepository.countByHostAndStatus(host, AdventureStatus.COMPLETED);

        long acceptedCount = joinRequestRepository.countByAdventureAndStatus(
                adventure, JoinRequestStatus.ACCEPTED);
        long slotsLeft   = Math.max(0, adventure.getMaxParticipants() - acceptedCount);
        int  fillPercent = adventure.getMaxParticipants() > 0
                ? (int) Math.min(100, acceptedCount * 100 / adventure.getMaxParticipants()) : 0;

        // Use findByAdventureAndRequester (Optional<JoinRequest>) instead of
        // existsByAdventureAndRequester (boolean) so we can distinguish
        // PENDING / ACCEPTED / REJECTED and show the correct sidebar notice.
        boolean isLoggedIn = (principal != null);
        JoinRequestStatus requestStatus = null;
        if (isLoggedIn && !isOwner) {
            requestStatus = joinRequestRepository
                    .findByAdventureAndRequester(adventure,
                            userRepository.getReferenceById(principal.getId()))
                    .map(JoinRequest::getStatus)
                    .orElse(null);
        }
        boolean alreadyRequested  = requestStatus != null;
        boolean isPendingRequest  = requestStatus == JoinRequestStatus.PENDING;
        boolean isAcceptedRequest = requestStatus == JoinRequestStatus.ACCEPTED;
        boolean isRejectedRequest = requestStatus == JoinRequestStatus.REJECTED;

        boolean isPublished    = adventure.getStatus() == AdventureStatus.PUBLISHED;
        boolean isCompleted    = adventure.getStatus() == AdventureStatus.COMPLETED;
        boolean isCancelled    = adventure.getStatus() == AdventureStatus.CANCELLED;
        boolean showJoinButton = isLoggedIn && !isOwner && !alreadyRequested
                                 && slotsLeft > 0 && isPublished;

        List<Adventure> related = adventureRepository
                .findPublishedByHostId(host.getId(), PageRequest.of(0, 4))
                .stream()
                .filter(a -> !a.getId().equals(adventure.getId()))
                .limit(3)
                .toList();

        List<String> hostBadges = new ArrayList<>();
        if (totalHosted    >= 1)  hostBadges.add("Adventure Leader");
        if (totalCompleted >= 3)  hostBadges.add("Trek Veteran");
        if (totalHosted    >= 5)  hostBadges.add("Explorer");
        if (totalCompleted >= 10) hostBadges.add("Summit Seeker");

        model.addAttribute("adventure",         adventure);
        model.addAttribute("host",              host);
        model.addAttribute("totalHosted",       totalHosted);
        model.addAttribute("totalCompleted",    totalCompleted);
        model.addAttribute("hostBadges",        hostBadges);
        model.addAttribute("acceptedCount",     acceptedCount);
        model.addAttribute("slotsLeft",         slotsLeft);
        model.addAttribute("fillPercent",       fillPercent);
        model.addAttribute("alreadyRequested",  alreadyRequested);
        model.addAttribute("isPendingRequest",  isPendingRequest);
        model.addAttribute("isAcceptedRequest", isAcceptedRequest);
        model.addAttribute("isRejectedRequest", isRejectedRequest);
        model.addAttribute("isOwner",           isOwner);
        model.addAttribute("isLoggedIn",        isLoggedIn);
        model.addAttribute("isPublished",       isPublished);
        model.addAttribute("isCompleted",       isCompleted);
        model.addAttribute("isCancelled",       isCancelled);
        model.addAttribute("showJoinButton",    showJoinButton);
        model.addAttribute("isFull",            slotsLeft == 0);
        model.addAttribute("relatedAdventures", related);
        model.addAttribute("userName",          principal != null ? principal.getName() : null);

        return "adventure-details";
    }

    // ── Submit Join Request ───────────────────────────────────────────────────
    // Requires ROLE_USER (anyRequest catch-all in SecurityConfig).
    // Guards mirror AdventureController.submitJoin to prevent bypass via the
    // modal endpoint.

    @PostMapping("/adventures/{id}/join-request")
    @Transactional
    public String submitJoinRequest(@PathVariable Long id,
                                    @RequestParam(required = false) String message,
                                    @AuthenticationPrincipal CustomUserDetails principal,
                                    RedirectAttributes redirectAttrs) {

        log.debug("submitJoinRequest → adventureId={} requesterId={}", id, principal.getId());

        Adventure adventure = adventureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (adventure.getStatus() != AdventureStatus.PUBLISHED) {
            log.debug("submitJoinRequest rejected: adventure {} is {}", id, adventure.getStatus());
            redirectAttrs.addFlashAttribute("joinError", "This adventure is not accepting requests.");
            return "redirect:/adventures/" + id;
        }

        if (adventure.getHost().getId().equals(principal.getId())) {
            log.debug("submitJoinRequest rejected: requester {} is the host", principal.getId());
            redirectAttrs.addFlashAttribute("joinError", "You cannot join your own adventure.");
            return "redirect:/adventures/" + id;
        }

        User requester = userRepository.getReferenceById(principal.getId());

        if (joinRequestRepository.existsByAdventureAndRequester(adventure, requester)) {
            log.debug("submitJoinRequest rejected: duplicate request for adventure {} by requester {}",
                    id, principal.getId());
            redirectAttrs.addFlashAttribute("joinError", "You have already sent a request for this adventure.");
            return "redirect:/adventures/" + id;
        }

        long accepted = joinRequestRepository.countByAdventureAndStatus(adventure, JoinRequestStatus.ACCEPTED);
        if (accepted >= adventure.getMaxParticipants()) {
            log.debug("submitJoinRequest rejected: adventure {} is full ({}/{})",
                    id, accepted, adventure.getMaxParticipants());
            redirectAttrs.addFlashAttribute("joinError", "This adventure is full.");
            return "redirect:/adventures/" + id;
        }

        JoinRequest jr = joinRequestRepository.save(
                JoinRequest.builder()
                        .adventure(adventure)
                        .requester(requester)
                        .message(message != null && !message.isBlank() ? message.trim() : null)
                        .build());

        log.info("JoinRequest saved → id={} adventureId={} requesterId={} status={}",
                jr.getId(), id, principal.getId(), jr.getStatus());

        notificationService.notifyUser(
                adventure.getHost().getId(),
                "New Join Request",
                principal.getName() + " wants to join your adventure.",
                NotificationType.REQUEST_RECEIVED,
                "/adventures/" + id + "/requests"
        );

        redirectAttrs.addFlashAttribute("joinSuccess", true);
        return "redirect:/adventures/" + id;
    }
}
