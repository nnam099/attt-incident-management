package com.attt.incident.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Custom {@link UserDetails} implementation that carries the user's
 * {@code token_version} from the database.
 *
 * <p>The {@code tokenVersion} is embedded into every issued JWT as the claim
 * {@code "tv"}. {@link JwtAuthFilter} rejects any token whose {@code tv}
 * claim is less than the current value stored in the database, effectively
 * invalidating all previously issued tokens for that user without rotating
 * the signing key.
 *
 * <p>This class is intentionally immutable after construction.
 */
public final class AppUserPrincipal implements UserDetails {

    private final long userId;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;
    /** Current token_version from the users table. */
    private final long tokenVersion;

    public AppUserPrincipal(long userId,
                            String username,
                            String password,
                            boolean enabled,
                            Collection<? extends GrantedAuthority> authorities,
                            long tokenVersion) {
        this.userId       = userId;
        this.username     = username;
        this.password     = password;
        this.enabled      = enabled;
        this.authorities  = Collections.unmodifiableCollection(authorities);
        this.tokenVersion = tokenVersion;
    }

    // ── Getters ─────────────────────────────────────────────────────

    public long getUserId() {
        return userId;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    // ── UserDetails contract ─────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    /** Account is non-expired; expiry is managed via enabled flag + lock. */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Account lock is enforced by {@link LoginAttemptService} at the
     * controller layer. Spring Security's "locked" state is not used
     * to avoid leaking lock status in 401 vs 403 differentiation.
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /** Credential expiry is not enforced at this layer. */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
