package com.example.todo.controller;

import com.example.todo.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CreateTaskIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    WebApplicationContext wac;

    MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    // AC1: valid body with all fields set → 201, body matches, version=0, id UUID, Location header
    @Test
    void createTask_allFieldsValid_returns201() throws Exception {
        String body = """
                {
                  "title": "Buy groceries",
                  "description": "Milk and eggs",
                  "status": "IN_PROGRESS",
                  "priority": "HIGH",
                  "dueDate": "2027-01-15",
                  "tags": ["shopping", "urgent"]
                }
                """;
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Buy groceries"))
                .andExpect(jsonPath("$.description").value("Milk and eggs"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.dueDate").value("2027-01-15"))
                .andExpect(jsonPath("$.tags", hasItems("shopping", "urgent")))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/tasks/[0-9a-f-]{36}")));
    }

    // AC2: only title → status=TODO, priority=MEDIUM, dueDate=null, tags=[]
    @Test
    void createTask_onlyTitle_appliesDefaults() throws Exception {
        String body = """
                {"title": "Simple task"}
                """;
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.dueDate").value(nullValue()))
                .andExpect(jsonPath("$.tags", empty()));
    }

    // AC3: blank title → 400 VALIDATION_FAILED with field error for title
    @Test
    void createTask_blankTitle_returns400ValidationFailed() throws Exception {
        String body = """
                {"title": "   "}
                """;
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists());
    }

    // AC3: omitted title → 400 VALIDATION_FAILED with field error for title
    @Test
    void createTask_omittedTitle_returns400ValidationFailed() throws Exception {
        String body = """
                {"description": "No title here"}
                """;
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists());
    }

    // AC4: title 201 characters → 400 VALIDATION_FAILED
    @Test
    void createTask_titleTooLong_returns400ValidationFailed() throws Exception {
        String longTitle = "A".repeat(201);
        String body = "{\"title\": \"" + longTitle + "\"}";
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists());
    }

    // AC5: description 4001 characters → 400 VALIDATION_FAILED
    @Test
    void createTask_descriptionTooLong_returns400ValidationFailed() throws Exception {
        String longDesc = "B".repeat(4001);
        String body = "{\"title\": \"Valid\", \"description\": \"" + longDesc + "\"}";
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'description')]").exists());
    }

    // AC6: status = "done" (lowercase) → 400 MALFORMED_REQUEST
    @Test
    void createTask_lowercaseStatus_returns400MalformedRequest() throws Exception {
        String body = """
                {"title": "Test", "status": "done"}
                """;
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // AC6: non-enum string → 400 MALFORMED_REQUEST
    @Test
    void createTask_invalidStatusString_returns400MalformedRequest() throws Exception {
        String body = """
                {"title": "Test", "status": "INVALID_STATUS"}
                """;
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // AC7: dueDate wrong format → 400 MALFORMED_REQUEST
    @Test
    void createTask_wrongDateFormat_returns400MalformedRequest() throws Exception {
        String body = """
                {"title": "Test", "dueDate": "31/09/2026"}
                """;
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // AC8: 11 tags → 400 VALIDATION_FAILED
    @Test
    void createTask_elevenTags_returns400ValidationFailed() throws Exception {
        String tags = IntStream.rangeClosed(1, 11)
                .mapToObj(i -> "\"tag" + i + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
        String body = "{\"title\": \"Test\", \"tags\": " + tags + "}";
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'tags')]").exists());
    }

    // AC9: invalid tag character → 400 VALIDATION_FAILED naming that tag
    @Test
    void createTask_invalidTagCharacter_returns400ValidationFailedNamingTag() throws Exception {
        String body = """
                {"title": "Test", "tags": ["finance!"]}
                """;
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].message", containsString("finance!")));
    }

    // AC10: duplicate tags normalised → deduplicated, lowercased
    @Test
    void createTask_duplicateTags_normalised() throws Exception {
        String body = """
                {"title": "Test", "tags": ["Finance", " finance ", "FINANCE"]}
                """;
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tags", hasSize(1)))
                .andExpect(jsonPath("$.tags[0]").value("finance"));
    }

    // AC11: unknown field → 400 VALIDATION_FAILED
    @Test
    void createTask_unknownField_returns400ValidationFailed() throws Exception {
        String body = """
                {"title": "Test", "priorty": "HIGH"}
                """;
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'priorty')]").exists());
    }

    // AC12: status=DONE on create → completedAt non-null
    @Test
    void createTask_statusDone_completedAtSet() throws Exception {
        String body = """
                {"title": "Done task", "status": "DONE"}
                """;
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    // AC13: Content-Type: text/plain → 415 UNSUPPORTED_MEDIA_TYPE
    @Test
    void createTask_wrongContentType_returns415() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"title\": \"Test\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // AC14: body > 64 KB → 413 PAYLOAD_TOO_LARGE
    @Test
    void createTask_bodyTooLarge_returns413() throws Exception {
        byte[] largeBody = ("X".repeat(66_000)).getBytes();
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Content-Length", String.valueOf(largeBody.length))
                        .content(largeBody))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    // AC15: malformed JSON → 400 MALFORMED_REQUEST
    @Test
    void createTask_malformedJson_returns400MalformedRequest() throws Exception {
        String body = "{\"title\": \"Test\",}";  // trailing comma
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // X-Request-Id header echoed in response
    @Test
    void createTask_requestIdEchoed() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "test-id-123")
                        .content("{\"title\": \"Test\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", "test-id-123"));
    }
}
