package com.example.todo.controller;

import com.example.todo.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReplaceTaskIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    WebApplicationContext wac;

    MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    // -----------------------------------------------------------------------
    // Helper: create a task and return its ID (version starts at 0)
    // -----------------------------------------------------------------------
    private String createTask(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        // extract id via simple substring (avoids adding ObjectMapper dependency)
        int start = response.indexOf("\"id\":\"") + 6;
        int end = response.indexOf("\"", start);
        return response.substring(start, end);
    }

    private String defaultBody(String status, long version) {
        return """
                {
                  "title": "Replaced title",
                  "status": "%s",
                  "priority": "HIGH",
                  "version": %d
                }
                """.formatted(status, version);
    }

    // -----------------------------------------------------------------------
    // AC1: valid full body with correct version → 200, all fields replaced, version incremented
    // -----------------------------------------------------------------------
    @Test
    void replace_validFullBody_returns200AndIncrementsVersion() throws Exception {
        String id = createTask("{\"title\": \"Original\", \"description\": \"desc\", \"tags\": [\"old\"]}");

        String body = """
                {
                  "title": "Updated Title",
                  "description": "New description",
                  "status": "IN_PROGRESS",
                  "priority": "HIGH",
                  "dueDate": "2027-06-01",
                  "tags": ["new", "tag"],
                  "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.description").value("New description"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.dueDate").value("2027-06-01"))
                .andExpect(jsonPath("$.tags", hasItems("new", "tag")))
                .andExpect(jsonPath("$.version").value(1));
    }

    // -----------------------------------------------------------------------
    // AC2: body omits optional fields → they reset to null/null/[]
    // -----------------------------------------------------------------------
    @Test
    void replace_omittedOptionalFields_resetsToDefaults() throws Exception {
        String id = createTask("{\"title\": \"Task\", \"description\": \"Some desc\", \"tags\": [\"old\"]}");

        String body = """
                {
                  "title": "Minimal",
                  "status": "TODO",
                  "priority": "MEDIUM",
                  "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.dueDate").value(nullValue()))
                .andExpect(jsonPath("$.tags", empty()));
    }

    // -----------------------------------------------------------------------
    // AC3: missing required fields → 400 VALIDATION_FAILED
    // -----------------------------------------------------------------------
    @Test
    void replace_missingTitle_returns400ValidationFailed() throws Exception {
        String id = createTask("{\"title\": \"Task\"}");

        String body = """
                {
                  "status": "TODO",
                  "priority": "MEDIUM",
                  "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists());
    }

    @Test
    void replace_missingStatus_returns400ValidationFailed() throws Exception {
        String id = createTask("{\"title\": \"Task\"}");

        String body = """
                {
                  "title": "Task",
                  "priority": "MEDIUM",
                  "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'status')]").exists());
    }

    @Test
    void replace_missingPriority_returns400ValidationFailed() throws Exception {
        String id = createTask("{\"title\": \"Task\"}");

        String body = """
                {
                  "title": "Task",
                  "status": "TODO",
                  "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'priority')]").exists());
    }

    @Test
    void replace_missingVersion_returns400ValidationFailed() throws Exception {
        String id = createTask("{\"title\": \"Task\"}");

        String body = """
                {
                  "title": "Task",
                  "status": "TODO",
                  "priority": "MEDIUM"
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'version')]").exists());
    }

    // -----------------------------------------------------------------------
    // AC4: version mismatch → 409 VERSION_CONFLICT, no change persisted
    // -----------------------------------------------------------------------
    @Test
    void replace_versionMismatch_returns409VersionConflict() throws Exception {
        String id = createTask("{\"title\": \"Task\"}");

        String body = """
                {
                  "title": "Changed",
                  "status": "TODO",
                  "priority": "MEDIUM",
                  "version": 99
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.detail", containsString("expected")))
                .andExpect(jsonPath("$.detail", containsString("found")));

        // verify title is unchanged
        mockMvc.perform(get("/api/v1/tasks/" + id))
                .andExpect(jsonPath("$.title").value("Task"));
    }

    // -----------------------------------------------------------------------
    // AC5: CANCELLED → IN_PROGRESS → 409 INVALID_STATUS_TRANSITION, no change
    // -----------------------------------------------------------------------
    @Test
    void replace_cancelledToInProgress_returns409InvalidStatusTransition() throws Exception {
        String id = createTask("{\"title\": \"Task\", \"status\": \"CANCELLED\"}");

        String body = """
                {
                  "title": "Task",
                  "status": "IN_PROGRESS",
                  "priority": "MEDIUM",
                  "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        // verify status is still CANCELLED
        mockMvc.perform(get("/api/v1/tasks/" + id))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // -----------------------------------------------------------------------
    // AC6: DONE → TODO (reopen) → 200, completedAt becomes null
    // -----------------------------------------------------------------------
    @Test
    void replace_doneToTodo_returns200AndClearsCompletedAt() throws Exception {
        String id = createTask("{\"title\": \"Task\", \"status\": \"DONE\"}");

        // verify completedAt was set on creation
        mockMvc.perform(get("/api/v1/tasks/" + id))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        String body = """
                {
                  "title": "Task",
                  "status": "TODO",
                  "priority": "MEDIUM",
                  "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").value(nullValue()));
    }

    // -----------------------------------------------------------------------
    // AC7: TODO → DONE → 200, completedAt non-null
    // -----------------------------------------------------------------------
    @Test
    void replace_todoToDone_returns200AndSetsCompletedAt() throws Exception {
        String id = createTask("{\"title\": \"Task\"}");

        String body = """
                {
                  "title": "Task",
                  "status": "DONE",
                  "priority": "MEDIUM",
                  "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    // -----------------------------------------------------------------------
    // AC8: same status as stored → 200 (no-op transition is not an error)
    // -----------------------------------------------------------------------
    @Test
    void replace_sameStatus_returns200() throws Exception {
        String id = createTask("{\"title\": \"Task\", \"status\": \"IN_PROGRESS\"}");

        String body = """
                {
                  "title": "Task",
                  "status": "IN_PROGRESS",
                  "priority": "MEDIUM",
                  "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    // -----------------------------------------------------------------------
    // AC9: unknown field in body → 400 VALIDATION_FAILED
    // -----------------------------------------------------------------------
    @Test
    void replace_unknownFieldId_returns400ValidationFailed() throws Exception {
        String id = createTask("{\"title\": \"Task\"}");

        String body = """
                {
                  "id": "some-id",
                  "title": "Task",
                  "status": "TODO",
                  "priority": "MEDIUM",
                  "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void replace_unknownFieldCreatedAt_returns400ValidationFailed() throws Exception {
        String id = createTask("{\"title\": \"Task\"}");

        String body = """
                {
                  "createdAt": "2026-01-01T00:00:00Z",
                  "title": "Task",
                  "status": "TODO",
                  "priority": "MEDIUM",
                  "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // -----------------------------------------------------------------------
    // AC10: non-existent valid UUID → 404 TASK_NOT_FOUND
    // -----------------------------------------------------------------------
    @Test
    void replace_nonExistentId_returns404TaskNotFound() throws Exception {
        String body = defaultBody("TODO", 0);

        mockMvc.perform(put("/api/v1/tasks/00000000-0000-0000-0000-000000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    // -----------------------------------------------------------------------
    // AC11: malformed path id → 400 INVALID_ID_FORMAT
    // -----------------------------------------------------------------------
    @Test
    void replace_malformedPathId_returns400InvalidIdFormat() throws Exception {
        String body = defaultBody("TODO", 0);

        mockMvc.perform(put("/api/v1/tasks/not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ID_FORMAT"));
    }

    // -----------------------------------------------------------------------
    // Status transition matrix (4x4 minus same-status which is always allowed)
    // -----------------------------------------------------------------------

    // From TODO: can go to IN_PROGRESS, DONE, CANCELLED
    @ParameterizedTest(name = "TODO -> {0} = {1}")
    @CsvSource({
            "IN_PROGRESS, 200",
            "DONE,        200",
            "CANCELLED,   200"
    })
    void transition_fromTodo(String toStatus, int expectedStatus) throws Exception {
        String id = createTask("{\"title\": \"T\", \"status\": \"TODO\"}");
        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultBody(toStatus, 0)))
                .andExpect(status().is(expectedStatus));
    }

    // From IN_PROGRESS: can go to TODO, DONE, CANCELLED
    @ParameterizedTest(name = "IN_PROGRESS -> {0} = {1}")
    @CsvSource({
            "TODO,      200",
            "DONE,      200",
            "CANCELLED, 200"
    })
    void transition_fromInProgress(String toStatus, int expectedStatus) throws Exception {
        String id = createTask("{\"title\": \"T\", \"status\": \"IN_PROGRESS\"}");
        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultBody(toStatus, 0)))
                .andExpect(status().is(expectedStatus));
    }

    // From DONE: can go to TODO, IN_PROGRESS
    @ParameterizedTest(name = "DONE -> {0} = {1}")
    @CsvSource({
            "TODO,        200",
            "IN_PROGRESS, 200",
            "CANCELLED,   409"
    })
    void transition_fromDone(String toStatus, int expectedStatus) throws Exception {
        String id = createTask("{\"title\": \"T\", \"status\": \"DONE\"}");
        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultBody(toStatus, 0)))
                .andExpect(status().is(expectedStatus));
    }

    // From CANCELLED: no transitions out
    @ParameterizedTest(name = "CANCELLED -> {0} = {1}")
    @CsvSource({
            "TODO,        409",
            "IN_PROGRESS, 409",
            "DONE,        409"
    })
    void transition_fromCancelled(String toStatus, int expectedStatus) throws Exception {
        String id = createTask("{\"title\": \"T\", \"status\": \"CANCELLED\"}");
        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultBody(toStatus, 0)))
                .andExpect(status().is(expectedStatus));
    }
}
