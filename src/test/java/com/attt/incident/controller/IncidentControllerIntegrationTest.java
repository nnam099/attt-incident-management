package com.attt.incident.controller;

import com.attt.incident.BaseIntegrationTest;
import com.attt.incident.dto.IncidentCreateRequest;
import com.attt.incident.entity.IncidentSeverity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidentControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "reporter1", roles = {"REPORTER"})
    void createIncident_Success() throws Exception {
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setTitle("Lỗi kết nối CSDL");
        request.setDescription("Không thể query data từ bảng users");
        request.setSeverity(IncidentSeverity.HIGH);
        request.setCategoryId(1L);

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Lỗi kết nối CSDL"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.reportedBy").value("reporter1"));
    }

    @Test
    @WithMockUser(username = "reporter1", roles = {"REPORTER"})
    void createIncident_ValidationFailed() throws Exception {
        IncidentCreateRequest request = new IncidentCreateRequest();
        // Thiếu title, description...

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.severity").exists())
                .andExpect(jsonPath("$.errors.categoryId").exists());
    }
}
