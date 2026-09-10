package com.example.todo.domain;

import com.example.todo.AbstractIntegrationTest;
import com.example.todo.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers-backed integration tests validating the V1 Flyway migration schema
 * and Task JPA entity round-trip, covering all TODO-2 acceptance criteria.
 */
class TaskSchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TaskRepository taskRepository;

    // AC1: Flyway runs on startup — verified by context loading without error.
    // The @SpringBootTest context would fail if migrations didn't apply cleanly.

    // AC2: title = NULL fails NOT NULL constraint
    @Test
    void insertNullTitle_fails() {
        assertThatThrownBy(() ->
            jdbcTemplate.execute("INSERT INTO tasks (title, status, priority) VALUES (NULL, 'TODO', 'MEDIUM')")
        ).isInstanceOf(Exception.class)
         .hasMessageContaining("null value in column \"title\"");
    }

    // AC3: title = '   ' fails ck_tasks_title_not_blank
    @Test
    void insertBlankTitle_fails() {
        assertThatThrownBy(() ->
            jdbcTemplate.execute("INSERT INTO tasks (title, status, priority) VALUES ('   ', 'TODO', 'MEDIUM')")
        ).isInstanceOf(Exception.class)
         .hasMessageContaining("ck_tasks_title_not_blank");
    }

    // AC4: status = 'BOGUS' fails ck_tasks_status
    @Test
    void insertBogusStatus_fails() {
        assertThatThrownBy(() ->
            jdbcTemplate.execute("INSERT INTO tasks (title, status, priority) VALUES ('t', 'BOGUS', 'MEDIUM')")
        ).isInstanceOf(Exception.class)
         .hasMessageContaining("ck_tasks_status");
    }

    // AC5: priority = 'BOGUS' fails ck_tasks_priority
    @Test
    void insertBogusPriority_fails() {
        assertThatThrownBy(() ->
            jdbcTemplate.execute("INSERT INTO tasks (title, status, priority) VALUES ('t', 'TODO', 'BOGUS')")
        ).isInstanceOf(Exception.class)
         .hasMessageContaining("ck_tasks_priority");
    }

    // AC6: status = 'DONE' with completed_at = NULL fails ck_tasks_completed_at_consistent
    @Test
    void insertDoneWithNullCompletedAt_fails() {
        assertThatThrownBy(() ->
            jdbcTemplate.execute(
                "INSERT INTO tasks (title, status, priority, completed_at) " +
                "VALUES ('t', 'DONE', 'MEDIUM', NULL)")
        ).isInstanceOf(Exception.class)
         .hasMessageContaining("ck_tasks_completed_at_consistent");
    }

    // AC7: status = 'TODO' with non-null completed_at fails ck_tasks_completed_at_consistent
    @Test
    void insertTodoWithNonNullCompletedAt_fails() {
        assertThatThrownBy(() ->
            jdbcTemplate.execute(
                "INSERT INTO tasks (title, status, priority, completed_at) " +
                "VALUES ('t', 'TODO', 'MEDIUM', now())")
        ).isInstanceOf(Exception.class)
         .hasMessageContaining("ck_tasks_completed_at_consistent");
    }

    // AC8: 11 tags fails ck_tasks_tags_limit
    @Test
    void insertElevenTags_fails() {
        assertThatThrownBy(() ->
            jdbcTemplate.execute(
                "INSERT INTO tasks (title, status, priority, tags) " +
                "VALUES ('t', 'TODO', 'MEDIUM', ARRAY['a','b','c','d','e','f','g','h','i','j','k'])")
        ).isInstanceOf(Exception.class)
         .hasMessageContaining("ck_tasks_tags_limit");
    }

    // AC9: updated_at is updated by trigger when row is updated without explicit updated_at
    @Test
    void updateRow_triggerSetsUpdatedAt() throws InterruptedException {
        UUID id = UUID.randomUUID();
        jdbcTemplate.execute(
            "INSERT INTO tasks (id, title, status, priority) VALUES ('" + id + "', 'Original', 'TODO', 'MEDIUM')");

        OffsetDateTime before = jdbcTemplate.queryForObject(
            "SELECT updated_at FROM tasks WHERE id = '" + id + "'", OffsetDateTime.class);

        // Sleep long enough for now() to advance past the insert timestamp
        Thread.sleep(100);

        jdbcTemplate.execute("UPDATE tasks SET title = 'Updated' WHERE id = '" + id + "'");

        OffsetDateTime after = jdbcTemplate.queryForObject(
            "SELECT updated_at FROM tasks WHERE id = '" + id + "'", OffsetDateTime.class);

        assertThat(after).isAfter(before);
    }

    // AC10: search_vector matches to_tsquery('simple', 'buy') for title 'Buy milk'
    @Test
    void searchVector_matchesTsquery() {
        jdbcTemplate.execute(
            "INSERT INTO tasks (title, status, priority) VALUES ('Buy milk', 'TODO', 'MEDIUM')");

        Boolean matches = jdbcTemplate.queryForObject(
            "SELECT search_vector @@ to_tsquery('simple', 'buy') FROM tasks WHERE title = 'Buy milk'",
            Boolean.class);

        assertThat(matches).isTrue();
    }

    // AC11: Task entity round-trips all fields including a non-empty tags list via JPA
    @Test
    void taskEntity_roundTripsAllFields() {
        Task task = new Task();
        task.setTitle("Buy groceries");
        task.setDescription("Get milk, eggs, and bread");
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setPriority(TaskPriority.HIGH);
        task.setDueDate(LocalDate.of(2026, 12, 31));
        task.setTags(List.of("shopping", "urgent"));

        Task saved = taskRepository.save(task);
        taskRepository.flush();

        Task loaded = taskRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getTitle()).isEqualTo("Buy groceries");
        assertThat(loaded.getDescription()).isEqualTo("Get milk, eggs, and bread");
        assertThat(loaded.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(loaded.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(loaded.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(loaded.getTags()).containsExactly("shopping", "urgent");
        assertThat(loaded.getVersion()).isEqualTo(0L);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getCompletedAt()).isNull();
    }
}
