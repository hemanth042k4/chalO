package com.chalo.controller;

import com.chalo.dto.ProfileEditForm;
import com.chalo.model.Adventure;
import com.chalo.model.JoinRequest;
import com.chalo.model.User;
import com.chalo.repository.AdventureRepository;
import com.chalo.repository.JoinRequestRepository;
import com.chalo.repository.UserRepository;
import com.chalo.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository        userRepository;
    private final AdventureRepository   adventureRepository;
    private final JoinRequestRepository joinRequestRepository;

    // ── Profile ──────────────────────────────────────────────────────────────
    // Loads the full User entity (not getReferenceById) because the template
    // needs scalar fields — name, email, createdAt — that are not on the
    // security principal.
    //
    // Query strategy (all existing methods, no new repository queries):
    //   hosted  → findByHostOrderByAdventureDateDesc — ordered DESC by date,
    //             first 3 become the preview; .size() gives the total count.
    //   joined  → findAcceptedByRequesterWithAdventureAndHost — JOIN FETCHes
    //             adventure + host in one query; first 3 become preview.
    //   pending → findPendingByRequesterWithAdventureAndHost — only .size()
    //             is used; the list itself is not passed to the template.

    @GetMapping("/profile")
    @Transactional(readOnly = true)
    public String showProfile(@AuthenticationPrincipal CustomUserDetails principal,
                              Model model) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<Adventure>   hosted  = adventureRepository.findByHostOrderByAdventureDateDesc(user);
        List<JoinRequest> joined  = joinRequestRepository.findAcceptedByRequesterWithAdventureAndHost(user);
        List<JoinRequest> pending = joinRequestRepository.findPendingByRequesterWithAdventureAndHost(user);

        model.addAttribute("user",          user);
        model.addAttribute("userName",      user.getName());
        model.addAttribute("hostedCount",   hosted.size());
        model.addAttribute("joinedCount",   joined.size());
        model.addAttribute("pendingCount",  pending.size());
        model.addAttribute("hostedPreview", hosted.subList(0, Math.min(3, hosted.size())));
        model.addAttribute("joinedPreview", joined.subList(0, Math.min(3, joined.size())));

        return "profile";
    }

    // ── Edit Profile — show form ──────────────────────────────────────────────

    @GetMapping("/profile/edit")
    @Transactional(readOnly = true)
    public String showEditForm(@AuthenticationPrincipal CustomUserDetails principal,
                               Model model) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!model.containsAttribute("form")) {
            ProfileEditForm form = new ProfileEditForm();
            form.setName(user.getName());
            form.setBio(user.getBio());
            model.addAttribute("form", form);
        }
        model.addAttribute("userName", user.getName());
        return "profile-edit";
    }

    // ── Edit Profile — save ───────────────────────────────────────────────────

    @PostMapping("/profile/edit")
    @Transactional
    public String saveProfile(@Valid @ModelAttribute("form") ProfileEditForm form,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal CustomUserDetails principal,
                              Model model,
                              RedirectAttributes redirectAttrs) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("userName", principal.getName());
            return "profile-edit";
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        user.setName(form.getName().trim());
        user.setBio(form.getBio() != null && !form.getBio().isBlank() ? form.getBio().trim() : null);

        redirectAttrs.addFlashAttribute("profileSuccess", "Profile updated successfully.");
        return "redirect:/profile";
    }
}
