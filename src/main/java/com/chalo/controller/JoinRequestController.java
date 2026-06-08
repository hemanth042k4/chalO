package com.chalo.controller;

import com.chalo.model.Adventure;
import com.chalo.model.JoinRequest;
import com.chalo.model.JoinRequestStatus;
import com.chalo.repository.JoinRequestRepository;
import com.chalo.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class JoinRequestController {

    private final JoinRequestRepository joinRequestRepository;

    // ── Accept ────────────────────────────────────────────────────────────────
    // Only the adventure's host may accept. The JOIN FETCH query avoids lazy-load
    // issues when reading adventure.host outside of open-session-in-view.

    @PostMapping("/requests/{id}/accept")
    @Transactional
    public String accept(@PathVariable Long id,
                          @AuthenticationPrincipal CustomUserDetails principal,
                          RedirectAttributes redirectAttrs) {

        JoinRequest request = joinRequestRepository.findByIdWithAdventureAndHost(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!request.getAdventure().getHost().getId().equals(principal.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Adventure adv = request.getAdventure();
        long accepted = joinRequestRepository.countByAdventureAndStatus(adv, JoinRequestStatus.ACCEPTED);
        if (accepted >= adv.getMaxParticipants()) {
            log.warn("accept: adventure {} already full ({}/{})", adv.getId(), accepted, adv.getMaxParticipants());
            redirectAttrs.addFlashAttribute("actionError",
                    "Cannot accept — this adventure is already full (" + accepted + "/" + adv.getMaxParticipants() + " slots taken).");
            return "redirect:/adventures/" + adv.getId() + "/requests";
        }

        // Dirty-checking emits the UPDATE on transaction commit — no explicit save needed.
        request.setStatus(JoinRequestStatus.ACCEPTED);
        log.info("accept: request {} accepted for adventure {} ({}/{})",
                id, adv.getId(), accepted + 1, adv.getMaxParticipants());

        redirectAttrs.addFlashAttribute("actionSuccess", "Request accepted.");
        return "redirect:/adventures/" + adv.getId() + "/requests";
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    @PostMapping("/requests/{id}/reject")
    @Transactional
    public String reject(@PathVariable Long id,
                          @AuthenticationPrincipal CustomUserDetails principal,
                          RedirectAttributes redirectAttrs) {

        JoinRequest request = joinRequestRepository.findByIdWithAdventureAndHost(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!request.getAdventure().getHost().getId().equals(principal.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        request.setStatus(JoinRequestStatus.REJECTED);

        redirectAttrs.addFlashAttribute("actionSuccess", "Request rejected.");
        return "redirect:/adventures/" + request.getAdventure().getId() + "/requests";
    }
}
