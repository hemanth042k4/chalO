package com.chalo.controller;

import com.chalo.dto.RegistrationForm;
import com.chalo.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    // ── Registration ─────────────────────────────────────────────────────────

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("form", new RegistrationForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String processRegistration(
            @Valid @ModelAttribute("form") RegistrationForm form,
            BindingResult bindingResult,
            HttpServletRequest  request,
            HttpServletResponse response) {

        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            authenticationService.register(form, request, response);
        } catch (AuthenticationService.EmailAlreadyInUseException e) {
            bindingResult.rejectValue(
                    "email",
                    "email.exists",
                    "This email is already registered. Please log in.");
            return "auth/register";
        }

        // Auto-login is complete — session already holds a valid SecurityContext
        return "redirect:/dashboard";
    }

    // ── User login ────────────────────────────────────────────────────────────
    // Spring Security processes POST /login automatically via UsernamePassword-
    // AuthenticationFilter. This controller only serves the GET (the login page).

    @GetMapping("/login")
    public String showLoginForm(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            Model model) {

        if (error != null) {
            model.addAttribute("errorMessage", "Invalid email or password.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "You have been logged out successfully.");
        }
        return "auth/login";
    }

    // ── Admin login ───────────────────────────────────────────────────────────
    // POST /admin/login is handled by Spring Security's admin filter chain
    // (SecurityConfig @Order(1)). This controller only serves the GET.

    @GetMapping("/admin/login")
    public String showAdminLoginForm(
            @RequestParam(required = false) String error,
            Model model) {

        if (error != null) {
            model.addAttribute("errorMessage",
                    "Invalid credentials or insufficient permissions.");
        }
        return "admin/login";
    }
}
