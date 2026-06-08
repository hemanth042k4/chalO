package com.chalo.security;

import com.chalo.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Stores only scalar fields from User so the HTTP session is safely
 * serializable — no JPA entity or lazy-loaded proxy is held in session.
 */
public class CustomUserDetails implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long   id;
    private final String name;
    private final String email;
    private final String password;
    private final boolean admin;

    public CustomUserDetails(User user) {
        this.id       = user.getId();
        this.name     = user.getName();
        this.email    = user.getEmail();
        this.password = user.getPassword();
        this.admin    = user.isAdmin();
    }

    // ── Convenience accessors for controllers ────────────────────────────────

    public Long getId()   { return id; }
    public String getName() { return name; }

    // ── UserDetails ──────────────────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String role = admin ? "ROLE_ADMIN" : "ROLE_USER";
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() { return password; }

    /** Spring Security treats this as the unique login identifier. */
    @Override
    public String getUsername() { return email; }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
