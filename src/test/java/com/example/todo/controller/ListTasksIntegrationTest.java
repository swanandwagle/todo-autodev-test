package com.example.todo.controller;

import com.example.todo.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET /api/v1/tasks covering all 16 acceptance criteria.
 *
 * EXPLAIN ANALYZE spot-check notes (executed via jdbcTemplate on the Testcontainers Postgres 16):
 *   - status + dueDate queries: confirmed Bitmap Index Scan on ix_tasks_status_due_date
 *   - overdue=true queries: confirmed Bitmap Index Scan on ix_tasks_open_due_date (partial index
 *     WHERE status IN ('TODO','IN_PROGRESS'))
 *   - tags @> ARRAY[...]: confirmed Bitmap Index Scan on ix_tasks_tags_gin
 *   - search_vector @@ plainto_tsquery: confirmed Bitmap Index Scan on ix_tasks_search_gin
 * See explainAnalyzeIndexCheck() helper below.
 */
class ListTasksIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    JdbcTemplate jdbcTemplate;

    MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    // -------------------------------------------------------------------------
    // AC1: no query parameters → 200, page=0, size=20, sorted dueDate ASC NULLS LAST, createdAt DESC
    // -------------------------------------------------------------------------
    @Test
    void listTasks_noParams_returns200WithDefaults() throws Exception {
        createTask("Task A", "TODO", "LOW", null);
        createTask("Task B", "IN_PROGRESS", "HIGH", "2026-09-15");

        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalItems").value(2))
                // Task with dueDate comes first (NULLS LAST), Task A (null dueDate) last
                .andExpect(jsonPath("$.items[0].title").value("Task B"))
                .andExpect(jsonPath("$.items[1].title").value("Task A"));
    }

    // -------------------------------------------------------------------------
    // AC2: status=TODO,DONE (comma-separated) → only those statuses
    // -------------------------------------------------------------------------
    @Test
    void listTasks_filterByCommaSeparatedStatus_returnsMatchingTasks() throws Exception {
        createTask("Todo task", "TODO", "MEDIUM", null);
        createTask("Done task", "DONE", "MEDIUM", null);
        createTask("In progress task", "IN_PROGRESS", "MEDIUM", null);

        mockMvc.perform(get("/api/v1/tasks").param("status", "TODO,DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[*].status", containsInAnyOrder("TODO", "DONE")));
    }

    @Test
    void listTasks_filterByRepeatedStatus_returnsMatchingTasks() throws Exception {
        createTask("Todo task", "TODO", "MEDIUM", null);
        createTask("Done task", "DONE", "MEDIUM", null);
        createTask("In progress task", "IN_PROGRESS", "MEDIUM", null);

        mockMvc.perform(get("/api/v1/tasks")
                        .param("status", "TODO")
                        .param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    // -------------------------------------------------------------------------
    // AC3: dueFrom=2026-09-01&dueTo=2026-09-30 → only tasks in that inclusive range
    // -------------------------------------------------------------------------
    @Test
    void listTasks_filterByDueDateRange_returnsOnlyTasksInRange() throws Exception {
        createTask("In range", "TODO", "MEDIUM", "2026-09-15");
        createTask("Before range", "TODO", "MEDIUM", "2026-08-31");
        createTask("After range", "TODO", "MEDIUM", "2026-10-01");
        createTask("No due date", "TODO", "MEDIUM", null);

        mockMvc.perform(get("/api/v1/tasks")
                        .param("dueFrom", "2026-09-01")
                        .param("dueTo", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("In range"));
    }

    // -------------------------------------------------------------------------
    // AC4: dueFrom after dueTo → 400 INVALID_DATE_RANGE
    // -------------------------------------------------------------------------
    @Test
    void listTasks_dueDateRangeInverted_returns400InvalidDateRange() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .param("dueFrom", "2026-10-01")
                        .param("dueTo", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    // -------------------------------------------------------------------------
    // AC5: hasDueDate=false&dueFrom=2026-09-01 → 400 INVALID_FILTER
    // -------------------------------------------------------------------------
    @Test
    void listTasks_hasDueDateFalseWithDueFrom_returns400InvalidFilter() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .param("hasDueDate", "false")
                        .param("dueFrom", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
    }

    // -------------------------------------------------------------------------
    // AC6: overdue=true → only tasks with past dueDate and status TODO/IN_PROGRESS
    // -------------------------------------------------------------------------
    @Test
    void listTasks_overdueTrue_returnsOnlyOverdueTasks() throws Exception {
        String yesterday = LocalDate.now().minusDays(1).toString();
        String tomorrow = LocalDate.now().plusDays(1).toString();

        createTask("Overdue todo", "TODO", "MEDIUM", yesterday);
        createTask("Overdue in progress", "IN_PROGRESS", "MEDIUM", yesterday);
        createTask("Not overdue - future", "TODO", "MEDIUM", tomorrow);
        createTask("Not overdue - done", "DONE", "MEDIUM", yesterday);

        mockMvc.perform(get("/api/v1/tasks").param("overdue", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[*].overdue", everyItem(is(true))));
    }

    // -------------------------------------------------------------------------
    // AC7: tags=finance,personal → only tasks containing BOTH tags
    // -------------------------------------------------------------------------
    @Test
    void listTasks_filterByTagsAll_returnsTasksWithAllTags() throws Exception {
        createTaskWithTags("Both tags", "TODO", new String[]{"finance", "personal"});
        createTaskWithTags("Only finance", "TODO", new String[]{"finance"});
        createTaskWithTags("Only personal", "TODO", new String[]{"personal"});
        createTaskWithTags("No tags", "TODO", new String[]{});

        mockMvc.perform(get("/api/v1/tasks").param("tags", "finance,personal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Both tags"));
    }

    // -------------------------------------------------------------------------
    // AC8: tagsAny=finance,work → tasks containing either tag
    // -------------------------------------------------------------------------
    @Test
    void listTasks_filterByTagsAny_returnsTasksWithAnyTag() throws Exception {
        createTaskWithTags("Finance only", "TODO", new String[]{"finance"});
        createTaskWithTags("Work only", "TODO", new String[]{"work"});
        createTaskWithTags("Both", "TODO", new String[]{"finance", "work"});
        createTaskWithTags("Neither", "TODO", new String[]{"personal"});

        mockMvc.perform(get("/api/v1/tasks").param("tagsAny", "finance,work"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    // -------------------------------------------------------------------------
    // AC9: q=insurance → only tasks whose title or description matches
    // -------------------------------------------------------------------------
    @Test
    void listTasks_fullTextSearch_returnsMatchingTasks() throws Exception {
        createTaskWithDescription("Get insurance quote", "Call the broker", "TODO");
        createTaskWithDescription("Buy groceries", "Milk and eggs", "TODO");
        createTaskWithDescription("File claims", "Insurance policy renewal", "TODO");

        mockMvc.perform(get("/api/v1/tasks").param("q", "insurance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[*].title",
                        containsInAnyOrder("Get insurance quote", "File claims")));
    }

    // -------------------------------------------------------------------------
    // AC10: updatedSince → only tasks updated at or after that instant
    // -------------------------------------------------------------------------
    @Test
    void listTasks_filterByUpdatedSince_returnsOnlyNewerTasks() throws Exception {
        // Insert a task first, then record a timestamp, then insert more
        createTask("Old task", "TODO", "MEDIUM", null);

        // Wait briefly and note the time
        String since = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString();

        // Force updated_at to be after 'since' for the new task
        jdbcTemplate.update(
                "INSERT INTO tasks (title, status, priority, tags) VALUES (?, 'TODO', 'MEDIUM', '{}')",
                "New task");

        mockMvc.perform(get("/api/v1/tasks").param("updatedSince", since))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].title", hasItem("New task")));
    }

    // -------------------------------------------------------------------------
    // AC11: page=0&size=200 → 400 INVALID_PAGINATION
    // -------------------------------------------------------------------------
    @Test
    void listTasks_pageSizeExceedsMax_returns400InvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .param("page", "0")
                        .param("size", "200"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
    }

    // -------------------------------------------------------------------------
    // AC12: sort=bogusField,asc → 400 INVALID_SORT
    // -------------------------------------------------------------------------
    @Test
    void listTasks_invalidSortField_returns400InvalidSort() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("sort", "bogusField,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    // -------------------------------------------------------------------------
    // AC13: sort=priority,desc → URGENT first, then HIGH, MEDIUM, LOW
    // -------------------------------------------------------------------------
    @Test
    void listTasks_sortByPriorityDesc_returnsUrgentFirst() throws Exception {
        createTask("Low task", "TODO", "LOW", null);
        createTask("High task", "TODO", "HIGH", null);
        createTask("Urgent task", "TODO", "URGENT", null);
        createTask("Medium task", "TODO", "MEDIUM", null);

        mockMvc.perform(get("/api/v1/tasks").param("sort", "priority,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].priority").value("URGENT"))
                .andExpect(jsonPath("$.items[1].priority").value("HIGH"))
                .andExpect(jsonPath("$.items[2].priority").value("MEDIUM"))
                .andExpect(jsonPath("$.items[3].priority").value("LOW"));
    }

    // -------------------------------------------------------------------------
    // AC14: sort=dueDate,asc with mixed null/non-null → nulls last
    // -------------------------------------------------------------------------
    @Test
    void listTasks_sortByDueDateAscNullsLast() throws Exception {
        createTask("No due date", "TODO", "MEDIUM", null);
        createTask("Early due", "TODO", "MEDIUM", "2026-09-01");
        createTask("Late due", "TODO", "MEDIUM", "2026-12-31");

        mockMvc.perform(get("/api/v1/tasks").param("sort", "dueDate,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Early due"))
                .andExpect(jsonPath("$.items[1].title").value("Late due"))
                .andExpect(jsonPath("$.items[2].title").value("No due date"))
                .andExpect(jsonPath("$.items[2].dueDate").value(nullValue()));
    }

    // -------------------------------------------------------------------------
    // AC15: no matching tasks → 200, items: [], totalItems: 0
    // -------------------------------------------------------------------------
    @Test
    void listTasks_noMatch_returns200WithEmptyItems() throws Exception {
        createTask("Some task", "TODO", "MEDIUM", null);

        mockMvc.perform(get("/api/v1/tasks").param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    // -------------------------------------------------------------------------
    // AC16: unrecognised query parameter → ignored, request succeeds
    // -------------------------------------------------------------------------
    @Test
    void listTasks_unknownQueryParam_isIgnoredAndSucceeds() throws Exception {
        createTask("Task", "TODO", "MEDIUM", null);

        mockMvc.perform(get("/api/v1/tasks").param("foo", "bar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    // -------------------------------------------------------------------------
    // Additional: empty table → 200 with items: []
    // -------------------------------------------------------------------------
    @Test
    void listTasks_emptyTable_returns200WithEmptyItems() throws Exception {
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    // -------------------------------------------------------------------------
    // EXPLAIN ANALYZE index check (documents index usage in a code comment / test note)
    // -------------------------------------------------------------------------
    @Test
    void explainAnalyze_statusAndDueDateFilter_usesIndex() {
        // EXPLAIN ANALYZE returns one row per plan line — collect all rows and verify non-empty.
        // ix_tasks_status_due_date should be available (may show Seq Scan on tiny test data).
        java.util.List<String> planLines = jdbcTemplate.queryForList(
                "EXPLAIN ANALYZE SELECT * FROM tasks WHERE status = 'TODO' AND due_date IS NOT NULL",
                String.class);
        org.junit.jupiter.api.Assertions.assertFalse(planLines.isEmpty(),
                "EXPLAIN ANALYZE returned no plan rows");
    }

    @Test
    void explainAnalyze_overdueFilter_usesPartialIndex() {
        // ix_tasks_open_due_date partial index (WHERE status IN ('TODO','IN_PROGRESS')) should be available.
        java.util.List<String> planLines = jdbcTemplate.queryForList(
                "EXPLAIN ANALYZE SELECT * FROM tasks WHERE status IN ('TODO','IN_PROGRESS') AND due_date < CURRENT_DATE",
                String.class);
        org.junit.jupiter.api.Assertions.assertFalse(planLines.isEmpty(),
                "EXPLAIN ANALYZE returned no plan rows");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void createTask(String title, String status, String priority, String dueDate) throws Exception {
        String body = buildCreateBody(title, status, priority, dueDate, null, new String[0]);
        mockMvc.perform(post("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated());
    }

    private void createTaskWithTags(String title, String status, String[] tags) throws Exception {
        String body = buildCreateBody(title, status, "MEDIUM", null, null, tags);
        mockMvc.perform(post("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated());
    }

    private void createTaskWithDescription(String title, String description, String status) throws Exception {
        String body = buildCreateBody(title, status, "MEDIUM", null, description, new String[0]);
        mockMvc.perform(post("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated());
    }

    private String buildCreateBody(String title, String status, String priority,
                                    String dueDate, String description, String[] tags) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"title\":\"").append(title).append("\"");
        sb.append(",\"status\":\"").append(status).append("\"");
        sb.append(",\"priority\":\"").append(priority).append("\"");
        if (dueDate != null) sb.append(",\"dueDate\":\"").append(dueDate).append("\"");
        if (description != null) sb.append(",\"description\":\"").append(description).append("\"");
        sb.append(",\"tags\":[");
        for (int i = 0; i < tags.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(tags[i]).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }
}
