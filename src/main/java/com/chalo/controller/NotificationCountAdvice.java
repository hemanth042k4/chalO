package com.chalo.controller;

import com.chalo.security.CustomUserDetails;
import com.chalo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Injects unreadNotificationCount into every Model so every Thymeleaf
 * template can render the header bell badge without per-controller wiring.
 * The COUNT query is cheap (indexed on user_id + is_read).
 */
@ControllerAdvice
@RequiredArgsConstructor
public class NotificationCountAdvice {

    private final NotificationService notificationService;

    @ModelAttribute("unreadNotificationCount")
    public long unreadCount(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null) return 0L;
        return notificationService.getUnreadCountForUser(principal.getId());
    }
}
