package com.chalo.controller;

import com.chalo.model.Notification;
import com.chalo.security.CustomUserDetails;
import com.chalo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // ── Notifications page ────────────────────────────────────────────────────

    @GetMapping("/notifications")
    @Transactional(readOnly = true)
    public String showNotifications(@AuthenticationPrincipal CustomUserDetails principal,
                                    Model model) {

        List<Notification> all = notificationService.getAllForUser(principal.getId());

        List<Notification> unread = all.stream().filter(n -> !n.isRead()).toList();
        List<Notification> read   = all.stream().filter(Notification::isRead).toList();

        Map<Long, String> timeAgo = all.stream()
                .collect(Collectors.toMap(
                        Notification::getId,
                        n -> NotificationService.timeAgo(n.getCreatedAt())
                ));

        model.addAttribute("unreadNotifications", unread);
        model.addAttribute("readNotifications",   read);
        model.addAttribute("timeAgo",             timeAgo);
        model.addAttribute("userName",            principal.getName());
        return "notifications";
    }

    // ── Click-to-navigate: marks as read then redirects to the notification's
    //    link. Returning to /notifications if the notification has no link.
    //    GET is appropriate here — this is an idempotent "open notification" action.

    @GetMapping("/notifications/{id}/go")
    @Transactional
    public String goToNotification(@PathVariable Long id,
                                   @AuthenticationPrincipal CustomUserDetails principal) {
        String link = notificationService.markAsReadAndGetLink(id, principal.getId());
        return (link != null && !link.isBlank()) ? "redirect:" + link : "redirect:/notifications";
    }

    // ── Mark a single notification as read (without navigating away) ──────────

    @PostMapping("/notifications/{id}/read")
    @Transactional
    public String markRead(@PathVariable Long id,
                           @AuthenticationPrincipal CustomUserDetails principal) {
        notificationService.markAsReadAndGetLink(id, principal.getId());
        return "redirect:/notifications";
    }

    // ── Mark all as read ──────────────────────────────────────────────────────

    @PostMapping("/notifications/read-all")
    @Transactional
    public String markAllRead(@AuthenticationPrincipal CustomUserDetails principal) {
        notificationService.markAllAsRead(principal.getId());
        return "redirect:/notifications";
    }
}
