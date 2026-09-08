package com.attt.incident.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

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

    @Test
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

    @Test
    void validJwt_SetsAuthentication_CallsChainOnce() throws ServletException, IOException {
        String token = "valid-jwt-token";
        request.addHeader("Authorization", "Bearer " + token);

        UserDetails userDetails = new User(
                "analyst1",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ANALYST"))
        );

        when(jwtService.extractUsername(token)).thenReturn("analyst1");
        when(userDetailsService.loadUserByUsername("analyst1")).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("analyst1", SecurityContextHolder.getContext().getAuthentication().getName());
        assertEquals(200, response.getStatus());

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void noAuthorizationHeader_CallsChainOnce_DoesNotAuthenticate() throws ServletException, IOException {
        // Request không có header Authorization
        jwtAuthFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, response.getStatus());

        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    void downstreamThrowsIllegalArgumentException_PropagatesException_DoesNotCallEntryPoint() throws ServletException, IOException {
        String token = "valid-jwt-token";
        request.addHeader("Authorization", "Bearer " + token);

        UserDetails userDetails = new User(
                "analyst1",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ANALYST"))
        );

        when(jwtService.extractUsername(token)).thenReturn("analyst1");
        when(userDetailsService.loadUserByUsername("analyst1")).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        RestAuthenticationEntryPoint mockEntryPoint = mock(RestAuthenticationEntryPoint.class);
        JwtAuthFilter filterWithMockEntryPoint = new JwtAuthFilter(jwtService, userDetailsService, mockEntryPoint);

        IllegalArgumentException downstreamException = new IllegalArgumentException("Dữ liệu đầu vào không hợp lệ từ controller");
        doThrow(downstreamException).when(filterChain).doFilter(request, response);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                filterWithMockEntryPoint.doFilter(request, response, filterChain)
        );

        assertSame(downstreamException, thrown);
        verify(mockEntryPoint, never()).commence(any(), any(), any());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void downstreamThrowsJwtException_PropagatesException_DoesNotConvert() throws ServletException, IOException {
        String token = "valid-jwt-token";
        request.addHeader("Authorization", "Bearer " + token);

        UserDetails userDetails = new User(
                "analyst1",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ANALYST"))
        );

        when(jwtService.extractUsername(token)).thenReturn("analyst1");
        when(userDetailsService.loadUserByUsername("analyst1")).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        RestAuthenticationEntryPoint mockEntryPoint = mock(RestAuthenticationEntryPoint.class);
        JwtAuthFilter filterWithMockEntryPoint = new JwtAuthFilter(jwtService, userDetailsService, mockEntryPoint);

        JwtException downstreamJwtException = new JwtException("Lỗi JWT nội bộ từ hệ thống SSO bên ngoài");
        doThrow(downstreamJwtException).when(filterChain).doFilter(request, response);

        JwtException thrown = assertThrows(JwtException.class, () ->
                filterWithMockEntryPoint.doFilter(request, response, filterChain)
        );

        assertSame(downstreamJwtException, thrown);
        verify(mockEntryPoint, never()).commence(any(), any(), any());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void authenticationEntryPoint_DoesNotWriteResponseIfAlreadyCommitted() throws IOException {
        MockHttpServletResponse committedResponse = spy(new MockHttpServletResponse());
        when(committedResponse.isCommitted()).thenReturn(true);

        authenticationEntryPoint.commence(request, committedResponse, null);

        verify(committedResponse, never()).setStatus(anyInt());
        verify(committedResponse, never()).getOutputStream();
        assertEquals(200, committedResponse.getStatus());
    }
}
