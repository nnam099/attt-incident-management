package com.attt.incident.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void preservesIsCompletedFieldExpectedByFrontend() throws Exception {
        TaskResponse response = TaskResponse.builder()
                .id(1L)
                .taskName("Task")
                .isCompleted(true)
                .build();

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"isCompleted\":true");
        assertThat(json).doesNotContain("\"completed\":true");
    }
}
