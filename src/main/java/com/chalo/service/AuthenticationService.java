package com.chalo.service;

import com.chalo.dto.RegistrationForm;
import com.chalo.model.User;
import com.chalo.repository.UserRepository;
import com.chalo.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository        userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    // Instantiated directly — no Spring bean required for this single use case
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    // ── Registration ─────────────────────────────────────────────────────────

    /**
     * Validates, persists the new user, then immediately authenticates them
     * so the controller can redirect straight to /dashboard without a login hop.
     *
     * @throws EmailAlreadyInUseException if the email is already registered
     */
    @Transactional
    public void register(RegistrationForm form,
                         HttpServletRequest  request,
                         HttpServletResponse response) {

        String email = form.getEmail().toLowerCase().trim();

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException(email);
        }

        User user = User.builder()
                .name(form.getName().trim())
                .email(email)
                .age(form.getAge())
                .password(passwordEncoder.encode(form.getPassword()))
                .build();

        userRepository.save(user);
        autoLogin(user, request, response);
    }

    // ── Programmatic login (used after registration) ──────────────────────────

    /**
     * Builds a pre-authenticated token and stores it in the HTTP session.
     * Uses UsernamePasswordAuthenticationToken.authenticated() — the static
     * factory that marks the token as already authenticated, bypassing the
     * AuthenticationManager entirely (correct for post-registration auto-login).
     */
    private void autoLogin(User user,
                           HttpServletRequest  request,
                           HttpServletResponse response) {

        CustomUserDetails userDetails = new CustomUserDetails(user);

        UsernamePasswordAuthenticationToken authToken =
                UsernamePasswordAuthenticationToken.authenticated(
                        userDetails,
                        null,                          // credentials cleared after auth
                        userDetails.getAuthorities()
                );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authToken);
        SecurityContextHolder.setContext(context);

        // Persist the SecurityContext to the JSESSIONID session so subsequent
        // requests are recognised as authenticated.
        securityContextRepository.saveContext(context, request, response);
    }

    // ── Exception ────────────────────────────────────────────────────────────

    public static class EmailAlreadyInUseException extends RuntimeException {
        public EmailAlreadyInUseException(String email) {
            super("Email already registered: " + email);
        }
    }
}
