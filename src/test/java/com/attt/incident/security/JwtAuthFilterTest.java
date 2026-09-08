package com.attt.incident.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JwtAuthFilter}.
 *
 * <p>All tests use {@link AppUserPrincipal} to exercise the token-version
 * and disabled-account guards introduced in Vòng bảo mật 2.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private AppUserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    private RestAuthenticationEntryPoint authenticationEntryPoint;
    private JwtAuthFilter jwtAuthFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Helper factory ───────────────────────────────────────────────

    /** Creates a default enabled principal with tokenVersion = 0. */
    private AppUserPrincipal principal(String username) {
        return new AppUserPrincipal(
                1L, username, "hash", true,
                List.of(new SimpleGrantedAuthority("ROLE_ANALYST")),
                0L);
    }

    /** Creates an enabled principal with a specific tokenVersion. */
    private AppUserPrincipal principal(String username, long tokenVersion) {
        return new AppUserPrincipal(
                1L, username, "hash", true,
                List.of(new SimpleGrantedAuthority("ROLE_ANALYST")),
                tokenVersion);
    }

    /** Creates a DISABLED principal. */
    private AppUserPrincipal disabledPrincipal(String username) {
        return new AppUserPrincipal(
                1L, username, "hash", false,
                List.of(new SimpleGrantedAuthority("ROLE_ANALYST")),
                0L);
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint = new RestAuthenticationEntryPoint(objectMapper);
        jwtAuthFilter = new JwtAuthFilter(jwtService, userDetailsService, authenticationEntryPoint);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Standard filter path ─────────────────────────────────────────

    @Test
    @DisplayName("No Authorization header → pass through, no authentication")
    void noAuthorizationHeader_CallsChainOnce_DoesNotAuthenticate() throws ServletException, IOException {
        jwtAuthFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, response.getStatus());

        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    @DisplayName("Bearer prefix absent → pass through, no authentication")
    void bearerPrefixAbsent_CallsChainOnce() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoInteractions(jwtService, userDetailsService);
    }

    // ── JWT parsing failures ─────────────────────────────────────────

    @Test
    @DisplayName("Malformed JWT → 401, chain never called")
    void malformedJwt_Returns401_DoesNotCallDownstreamChain() throws ServletException, IOException {
        String badToken = "malformed-jwt-token";
        request.addHeader("Authorization", "Bearer " + badToken);

        when(jwtService.extractUsername(badToken))
                .thenThrow(new MalformedJwtException("Malformed JWT token format"));

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"status\":401"));
        assertTrue(response.getContentAsString().contains("Unauthorized"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Unknown username in token → 401, chain never called")
    void unknownUser_Returns401_DoesNotCallDownstreamChain() throws ServletException, IOException {
        String token = "valid-token-for-unknown-user";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.extractUsername(token)).thenReturn("ghost_user");
        when(userDetailsService.loadUserByUsername("ghost_user"))
                .thenThrow(new UsernameNotFoundException("User not found"));

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"status\":401"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verify(filterChain, never()).doFilter(any(), any());
    }

    // ── Disabled account guard (Guard 1) ─────────────────────────────

    @Test
    @DisplayName("Disabled account + valid token → 401 (not 403), chain never called")
    void disabledAccount_Returns401_NotAuthenticated() throws ServletException, IOException {
        String token = "valid-token-disabled-user";
        request.addHeader("Authorization", "Bearer " + token);

        AppUserPrincipal disabled = disabledPrincipal("disabled_user");

        when(jwtService.extractUsername(token)).thenReturn("disabled_user");
        when(userDetailsService.loadUserByUsername("disabled_user")).thenReturn(disabled);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus(),
                "Disabled account must return 401, not 403, to avoid state disclosure");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, never()).doFilter(any(), any());
    }

    // ── Token validity guard (Guard 2) ────────────────────────────────

    @Test
    @DisplayName("Expired / invalid signature token → 401, chain never called")
    void expiredToken_Returns401() throws ServletException, IOException {
        String token = "expired-token";
        request.addHeader("Authorization", "Bearer " + token);

        AppUserPrincipal p = principal("analyst1");

        when(jwtService.extractUsername(token)).thenReturn("analyst1");
        when(userDetailsService.loadUserByUsername("analyst1")).thenReturn(p);
        when(jwtService.isTokenValid(token, p)).thenReturn(false);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, never()).doFilter(any(), any());
    }

    // ── Token version guard (Guard 3) ─────────────────────────────────

    @Test
    @DisplayName("Token tv claim < current DB version → 401 (token revoked)")
    void staleTokenVersion_Returns401() throws ServletException, IOException {
        String token = "stale-version-token";
        request.addHeader("Authorization", "Bearer " + token);

        // DB says tokenVersion = 3 (incremented after password change / admin disable)
        AppUserPrincipal p = principal("analyst1", 3L);

        when(jwtService.extractUsername(token)).thenReturn("analyst1");
        when(userDetailsService.loadUserByUsername("analyst1")).thenReturn(p);
        when(jwtService.isTokenValid(token, p)).thenReturn(true);
        // Token was issued with tv = 2, but current is 3
        when(jwtService.extractTokenVersion(token)).thenReturn(2L);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Token tv claim == current DB version → authenticated, chain called")
    void currentTokenVersion_Authenticates() throws ServletException, IOException {
        String token = "current-version-token";
        request.addHeader("Authorization", "Bearer " + token);

        AppUserPrincipal p = principal("analyst1", 2L);

        when(jwtService.extractUsername(token)).thenReturn("analyst1");
        when(userDetailsService.loadUserByUsername("analyst1")).thenReturn(p);
        when(jwtService.isTokenValid(token, p)).thenReturn(true);
        when(jwtService.extractTokenVersion(token)).thenReturn(2L);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("analyst1", SecurityContextHolder.getContext().getAuthentication().getName());
        assertEquals(200, response.getStatus());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // ── Happy path ────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid JWT, enabled account, matching tv → authenticated, chain called once")
    void validJwt_SetsAuthentication_CallsChainOnce() throws ServletException, IOException {
        String token = "valid-jwt-token";
        request.addHeader("Authorization", "Bearer " + token);

        AppUserPrincipal p = principal("analyst1");

        when(jwtService.extractUsername(token)).thenReturn("analyst1");
        when(userDetailsService.loadUserByUsername("analyst1")).thenReturn(p);
        when(jwtService.isTokenValid(token, p)).thenReturn(true);
        when(jwtService.extractTokenVersion(token)).thenReturn(0L);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("analyst1", SecurityContextHolder.getContext().getAuthentication().getName());
        assertEquals(200, response.getStatus());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // ── Exception boundary tests (Guard 2 of original design) ─────────

    @Test
    @DisplayName("Downstream IllegalArgumentException propagates — not swallowed as 401")
    void downstreamThrowsIllegalArgumentException_PropagatesException_DoesNotCallEntryPoint()
            throws ServletException, IOException {

        String token = "valid-jwt-token";
        request.addHeader("Authorization", "Bearer " + token);

        AppUserPrincipal p = principal("analyst1");

        when(jwtService.extractUsername(token)).thenReturn("analyst1");
        when(userDetailsService.loadUserByUsername("analyst1")).thenReturn(p);
        when(jwtService.isTokenValid(token, p)).thenReturn(true);
        when(jwtService.extractTokenVersion(token)).thenReturn(0L);

        RestAuthenticationEntryPoint mockEntryPoint = mock(RestAuthenticationEntryPoint.class);
        JwtAuthFilter filterWithMockEntryPoint =
                new JwtAuthFilter(jwtService, userDetailsService, mockEntryPoint);

        IllegalArgumentException downstreamException =
                new IllegalArgumentException("Dữ liệu đầu vào không hợp lệ từ controller");
        doThrow(downstreamException).when(filterChain).doFilter(request, response);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> filterWithMockEntryPoint.doFilter(request, response, filterChain));

        assertSame(downstreamException, thrown);
        verify(mockEntryPoint, never()).commence(any(), any(), any());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Downstream JwtException propagates — not swallowed as 401")
    void downstreamThrowsJwtException_PropagatesException_DoesNotConvert()
            throws ServletException, IOException {

        String token = "valid-jwt-token";
        request.addHeader("Authorization", "Bearer " + token);

        AppUserPrincipal p = principal("analyst1");

        when(jwtService.extractUsername(token)).thenReturn("analyst1");
        when(userDetailsService.loadUserByUsername("analyst1")).thenReturn(p);
        when(jwtService.isTokenValid(token, p)).thenReturn(true);
        when(jwtService.extractTokenVersion(token)).thenReturn(0L);

        RestAuthenticationEntryPoint mockEntryPoint = mock(RestAuthenticationEntryPoint.class);
        JwtAuthFilter filterWithMockEntryPoint =
                new JwtAuthFilter(jwtService, userDetailsService, mockEntryPoint);

        JwtException downstreamJwtException =
                new JwtException("Lỗi JWT nội bộ từ hệ thống SSO bên ngoài");
        doThrow(downstreamJwtException).when(filterChain).doFilter(request, response);

        JwtException thrown = assertThrows(JwtException.class,
                () -> filterWithMockEntryPoint.doFilter(request, response, filterChain));

        assertSame(downstreamJwtException, thrown);
        verify(mockEntryPoint, never()).commence(any(), any(), any());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // ── Entry point edge case ─────────────────────────────────────────

    @Test
    @DisplayName("Already-committed response → entry point does not write")
    void authenticationEntryPoint_DoesNotWriteResponseIfAlreadyCommitted() throws IOException {
        MockHttpServletResponse committedResponse = spy(new MockHttpServletResponse());
        when(committedResponse.isCommitted()).thenReturn(true);

        authenticationEntryPoint.commence(request, committedResponse, null);

        verify(committedResponse, never()).setStatus(anyInt());
        verify(committedResponse, never()).getOutputStream();
        assertEquals(200, committedResponse.getStatus());
    }
}
