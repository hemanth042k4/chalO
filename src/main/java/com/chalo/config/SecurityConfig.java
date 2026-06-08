package com.chalo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // ── Shared beans ─────────────────────────────────────────────────────────

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ── Admin filter chain ───────────────────────────────────────────────────
    // Intercepts /admin/** only. Must be ordered before the user chain.
    // Requires ROLE_ADMIN on all routes except the login page itself.

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/admin/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/login").permitAll()
                .anyRequest().hasRole("ADMIN")
            )
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .defaultSuccessUrl("/admin", true)
                .failureUrl("/admin/login?error")
            )
            .logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/admin/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );
        return http.build();
    }

    // ── User filter chain ────────────────────────────────────────────────────
    // Handles all routes not matched by the admin chain.
    // Public discovery routes are explicitly permitted so non-logged-in users
    // can browse adventures (experience-first product philosophy).

    @Bean
    @Order(2)
    public SecurityFilterChain userFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth

                // Static assets
                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()

                // Auth pages
                .requestMatchers("/login", "/register").permitAll()

                // Public discovery — anyone can browse
                .requestMatchers("/", "/explore").permitAll()
                .requestMatchers("/api/locations/suggest", "/api/tags").permitAll()

                // Adventure management routes must be listed BEFORE the broad
                // GET permit below, because Spring Security matches in order.
                .requestMatchers(
                    "/adventures/new",
                    "/adventures/*/edit",
                    "/adventures/*/requests",
                    "/adventures/*/requests/**",
                    "/adventures/*/chat",
                    "/adventures/*/chat/**"
                ).hasRole("USER")

                // Join submission requires a USER session (must be before the broad permitAll below)
                .requestMatchers(HttpMethod.POST, "/adventures/*/join").hasRole("USER")

                // Adventure detail and host profile are public browsing
                .requestMatchers("/adventures/*", "/host/**").permitAll()

                // All personal / transactional sections require a USER session
                .requestMatchers(
                    "/dashboard/**",
                    "/profile/**",
                    "/my-adventures/**",
                    "/chat/**"
                ).hasRole("USER")

                // Catch-all: anything not explicitly permitted requires USER
                .anyRequest().hasRole("USER")
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
            );
        return http.build();
    }
}
