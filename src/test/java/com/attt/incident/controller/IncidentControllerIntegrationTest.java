package com.attt.incident.controller;

import com.attt.incident.BaseIntegrationTest;
import com.attt.incident.dto.IncidentCreateRequest;
import com.attt.incident.dto.StatusUpdateRequest;
import com.attt.incident.entity.*;
import com.attt.incident.repository.IncidentCategoryRepository;
import com.attt.incident.repository.IncidentRepository;
import com.attt.incident.repository.RoleRepository;
import com.attt.incident.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IncidentControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private IncidentCategoryRepository categoryRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    private Long testCategoryId;
    private User reporter1User;
    private User reporter2User;
    private User analyst1User;
    private User analyst2User;
    private User helpdeskUser;
    private User managerUser;

    @BeforeEach
    void setUpFixtures() {
        Role reporterRole = getOrCreateRole(RoleName.REPORTER);
        Role analystRole = getOrCreateRole(RoleName.ANALYST);
        Role helpdeskRole = getOrCreateRole(RoleName.HELPDESK);
        Role managerRole = getOrCreateRole(RoleName.MANAGER);

        reporter1User = getOrCreateUser("reporter1", "reporter1@example.test", reporterRole);
        reporter2User = getOrCreateUser("reporter2", "reporter2@example.test", reporterRole);
        analyst1User = getOrCreateUser("analyst1", "analyst1@example.test", analystRole);
        analyst2User = getOrCreateUser("analyst2", "analyst2@example.test", analystRole);
        helpdeskUser = getOrCreateUser("helpdesk1", "helpdesk1@example.test", helpdeskRole);
        managerUser = getOrCreateUser("manager1", "manager1@example.test", managerRole);

        IncidentCategory category = categoryRepository.findAll().stream()
                .filter(c -> "Hệ thống / Phần mềm".equals(c.getName()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(IncidentCategory.builder()
                        .name("Hệ thống / Phần mềm")
                        .description("Lỗi hệ thống hoặc phần mềm")
                        .defaultSeverity(IncidentSeverity.MEDIUM)
                        .build()));
        testCategoryId = category.getId();
    }

    private Role getOrCreateRole(RoleName name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(new Role(null, name, name.name())));
    }

    private User getOrCreateUser(String username, String email, Role role) {
        return userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.save(User.builder()
                        .username(username)
                        .password("unused-in-mock-test")
                        .email(email)
                        .roles(Set.of(role))
                        .enabled(true)
                        .build()));
    }

    private Incident createTestIncident(User reporter, User assignee, IncidentStatus status) {
        return incidentRepository.save(Incident.builder()
                .incidentCode("INC-TEST-" + System.nanoTime())
                .title("Sự cố test " + System.currentTimeMillis())
                .description("Mô tả sự cố test")
                .category(categoryRepository.findById(testCategoryId).orElseThrow())
                .severity(IncidentSeverity.HIGH)
                .status(status)
                .reportedBy(reporter)
                .assignedTo(assignee)
                .build());
    }

    @Test
    @WithMockUser(username = "reporter1", roles = {"REPORTER"})
    void createIncident_Success() throws Exception {
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setTitle("Lỗi kết nối CSDL");
        request.setDescription("Không thể query data từ bảng users");
        request.setSeverity(IncidentSeverity.HIGH);
        request.setCategoryId(testCategoryId);

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Lỗi kết nối CSDL"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.reportedByUsername").value("reporter1"));
    }

    @Test
    @WithMockUser(username = "reporter1", roles = {"REPORTER"})
    void createIncident_ValidationFailed() throws Exception {
        IncidentCreateRequest request = new IncidentCreateRequest();
        // Thiếu title, description, categoryId

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.description").exists())
                .andExpect(jsonPath("$.errors.categoryId").exists())
                .andExpect(jsonPath("$.errors.severity").doesNotExist());
    }

    @Test
    void unauthenticatedRequest_Returns401Json() throws Exception {
        mockMvc.perform(get("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void malformedToken_Returns401Json() throws Exception {
        mockMvc.perform(get("/api/incidents")
                        .header("Authorization", "Bearer invalid-malformed-token-string")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @WithMockUser(username = "reporter1", roles = {"REPORTER"})
    void authenticatedUser_InsufficientPermissions_Returns403Json() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @WithMockUser(username = "reporter1", roles = {"REPORTER"})
    void reporterCanViewOwnIncident() throws Exception {
        Incident incident = createTestIncident(reporter1User, null, IncidentStatus.NEW);

        mockMvc.perform(get("/api/incidents/" + incident.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(incident.getId()))
                .andExpect(jsonPath("$.reportedByUsername").value("reporter1"));
    }

    @Test
    @WithMockUser(username = "reporter2", roles = {"REPORTER"})
    void reporterCannotViewAnotherReportersIncident() throws Exception {
        Incident incident = createTestIncident(reporter1User, null, IncidentStatus.NEW);

        mockMvc.perform(get("/api/incidents/" + incident.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "analyst2", roles = {"ANALYST"})
    void analystCannotManageUnassignedIncident() throws Exception {
        Incident incident = createTestIncident(reporter1User, analyst1User, IncidentStatus.INVESTIGATING);

        mockMvc.perform(post("/api/incidents/" + incident.getId() + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Analyst2 cố tình can thiệp trái phép\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "helpdesk1", roles = {"HELPDESK"})
    void validWorkflowTransition_Success() throws Exception {
        Incident incident = createTestIncident(reporter1User, null, IncidentStatus.NEW);

        StatusUpdateRequest request = new StatusUpdateRequest();
        request.setNewStatus(IncidentStatus.TRIAGE);
        request.setNote("Helpdesk tiếp nhận và bắt đầu phân loại");

        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAGE"));
    }

    @Test
    @WithMockUser(username = "manager1", roles = {"MANAGER"})
    void invalidWorkflowTransition_JumpToResolved_Returns400() throws Exception {
        Incident incident = createTestIncident(reporter1User, null, IncidentStatus.NEW);

        StatusUpdateRequest request = new StatusUpdateRequest();
        request.setNewStatus(IncidentStatus.RESOLVED);
        request.setNote("Cố tình nhảy cóc từ NEW sang RESOLVED");

        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
