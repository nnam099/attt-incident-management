package com.attt.incident.security;

import com.attt.incident.entity.User;
import com.attt.incident.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Loads an {@link AppUserPrincipal} from the database.
 *
 * <p>Unlike the Spring-built-in {@code User.builder()} approach, this service
 * returns a custom principal that carries {@code userId} and {@code tokenVersion},
 * both of which are required by {@link JwtService} and {@link JwtAuthFilter}
 * for per-user JWT invalidation.
 *
 * <p><strong>Security note:</strong> this method intentionally does NOT check
 * {@code enabled} here. The {@code enabled} flag is embedded in
 * {@link AppUserPrincipal#isEnabled()} and enforced by Spring Security's
 * {@code DaoAuthenticationProvider} at login time. For the JWT filter path,
 * the filter performs an explicit enabled-state check after loading the
 * principal, so that a disabled account is rejected with a generic 401
 * (not a 403) to avoid disclosing account state.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public AppUserPrincipal loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng"));
        // Intentionally omit the username in the exception message to prevent
        // username enumeration through error responses.

        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .collect(Collectors.toList());

        return new AppUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                authorities,
                user.getTokenVersion()
        );
    }
}
