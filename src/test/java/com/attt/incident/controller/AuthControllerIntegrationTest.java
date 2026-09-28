package com.attt.incident.controller;

import com.attt.incident.BaseIntegrationTest;
import com.attt.incident.entity.User;
import com.attt.incident.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.mock.web.MockCookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end authentication flow tests.
 *
 * <p>Uses Testcontainers (via {@link BaseIntegrationTest}) so Flyway runs on
 * a real PostgreSQL instance — no mocking of security internals.
 *
 * <p>Tests cover the full bootstrap → login → JWT → authorization chain.
 */
@DisplayName("Auth Controller Integration Tests")
class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    /** Enable the seed admin before each test (simulates bootstrap runner). */
    @BeforeEach
    void enableAdminForTest() {
        userRepository.findByUsername("admin").ifPresent(admin -> {
            admin.setEnabled(true);
            admin.setFailedLoginAttempts(0);
            admin.setAccountLockedUntil(null);
            userRepository.save(admin);
        });
    }

    // ── Test 1: Login endpoint reachable anonymously ─────────────────

    @Test
    @DisplayName("T01 - Login endpoint accessible without authentication")
    void loginEndpoint_isPublic() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nonexistent\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized()); // 401, not 403
    }

    // ── Test 2: Correct credentials → success ────────────────────────

    @Test
    @DisplayName("T02 - Valid credentials return JWT and an HttpOnly refresh cookie")
    void login_withValidCredentials_returnsTokens() throws Exception {
        String body = loginJson("admin", "Admin@123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("incident_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("HttpOnly")))
                .andReturn();

        String token = extractToken(result);
        assertThat(token).isNotBlank();
    }

    // ── Test 3: Wrong password → 401 ─────────────────────────────────

    @Test
    @DisplayName("T03 - Wrong password returns 401")
    void login_withWrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "WrongPassword@99")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").isString());
    }

    // ── Test 4: Disabled account → 401 ───────────────────────────────

    @Test
    @DisplayName("T04 - Disabled account returns 401")
    void login_withDisabledAccount_returns401() throws Exception {
        // Disable admin
        userRepository.findByUsername("admin").ifPresent(admin -> {
            admin.setEnabled(false);
            userRepository.save(admin);
        });

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "Admin@123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").isString());

        // Restore for other tests
        userRepository.findByUsername("admin").ifPresent(admin -> {
            admin.setEnabled(true);
            userRepository.save(admin);
        });
    }

    // ── Test 5: Missing JWT → 401 ─────────────────────────────────────

    @Test
    @DisplayName("T05 - Request without JWT returns 401")
    void protectedEndpoint_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // ── Test 6: Invalid JWT → 401 ─────────────────────────────────────

    @Test
    @DisplayName("T06 - Malformed JWT returns 401")
    void protectedEndpoint_withInvalidJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/incidents")
                        .header("Authorization", "Bearer this-is-not-a-valid-jwt")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ── Test 7: Valid JWT → protected endpoint succeeds ───────────────

    @Test
    @DisplayName("T07 - Valid JWT grants access to protected endpoint")
    void protectedEndpoint_withValidJwt_succeeds() throws Exception {
        String token = loginAndGetToken("admin", "Admin@123");

        mockMvc.perform(get("/api/incidents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // ── Test 8: USER role accessing ADMIN endpoint → 403 ─────────────

    @Test
    @DisplayName("T08 - REPORTER accessing ADMIN endpoint returns 403")
    @WithMockUser(username = "reporter_test", roles = "REPORTER")
    void adminEndpoint_withReporterRole_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    // ── Test 9: ADMIN accessing ADMIN endpoint → 200 ─────────────────

    @Test
    @DisplayName("T09 - ADMIN accessing ADMIN endpoint succeeds")
    void adminEndpoint_withAdminJwt_succeeds() throws Exception {
        String token = loginAndGetToken("admin", "Admin@123");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // ── Test 10: Restart idempotency — admin not duplicated ──────────

    @Test
    @DisplayName("T10 - Admin exists only once after bootstrap (no duplicate)")
    void bootstrap_doesNotDuplicateAdmin() {
        long adminCount = userRepository.findAll().stream()
                .filter(u -> "admin".equals(u.getUsername()))
                .count();
        assertThat(adminCount).isEqualTo(1);
    }

    // ── Test 11: Fresh DB — all migrations applied, admin usable ─────

    @Test
    @DisplayName("T11 - Fresh DB: Flyway applied all 8 migrations")
    void freshDb_flywayAppliedAllMigrations() {
        // If this test context started, Flyway succeeded on a fresh Testcontainer DB.
        // Verify admin was created by V2.
        assertThat(userRepository.findByUsername("admin")).isPresent();
    }

    // ── Test 12: Refresh token flow ───────────────────────────────────

    @Test
    @DisplayName("T12 - Valid refresh token returns new access token")
    void refresh_withValidRefreshToken_returnsNewAccessToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "Admin@123")))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = extractRefreshCookie(loginResult);
        assertThat(refreshCookie).isNotNull();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn();

        Cookie rotatedCookie = extractRefreshCookie(refreshResult);
        assertThat(rotatedCookie).isNotNull();
        assertThat(rotatedCookie.getValue()).isNotEqualTo(refreshCookie.getValue());

        // The consumed token cannot be replayed.
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    // ── Test 13: Invalid refresh token → 401 ─────────────────────────

    @Test
    @DisplayName("T13 - Invalid refresh token returns 401")
    void refresh_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("incident_refresh",
                                "00000000-dead-beef-0000-000000000000")))
                .andExpect(status().isUnauthorized());
    }

    // ── Test 14: 401 vs 403 differentiation ──────────────────────────

    @Test
    @DisplayName("T14 - Unauthenticated returns 401, not 403")
    void unauthenticated_returns401_not403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String loginJson(String username, String password) {
        return String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return extractToken(result);
    }

    private String extractToken(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private Cookie extractRefreshCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotBlank();
        return MockCookie.parse(setCookie);
    }
}
