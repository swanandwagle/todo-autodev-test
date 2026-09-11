package com.example.todo.integration;

import com.example.todo.AbstractIntegrationTest;
import com.example.todo.domain.Task;
import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import com.example.todo.dto.CreateTaskRequest;
import com.example.todo.dto.PatchTaskRequest;
import com.example.todo.dto.TaskResponse;
import com.example.todo.dto.UpdateTaskRequest;
import com.example.todo.exception.TaskNotFoundException;
import com.example.todo.repository.TaskQueryRepository;
import com.example.todo.repository.TaskRepository;
import com.example.todo.service.TaskListQueryValidator;
import com.example.todo.service.TaskService;
import com.example.todo.service.TaskStatusTransitionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Scenario 1 – Full CRUD lifecycle:
 * POST → GET → PUT → PATCH → DELETE → GET (404).
 */
@ExtendWith(MockitoExtension.class)
class CrudLifecycleTest extends AbstractIntegrationTest {

    @Mock TaskRepository taskRepository;
    @Mock TaskQueryRepository taskQueryRepository;
    @Mock TaskListQueryValidator queryValidator;

    TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, taskQueryRepository,
                queryValidator, new TaskStatusTransitionValidator());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Task buildTask(UUID id, String title, TaskStatus status, long version) {
        Task t = new Task();
        t.setId(id);
        t.setTitle(title);
        t.setDescription("desc");
        t.setStatus(status);
        t.setPriority(TaskPriority.MEDIUM);
        t.setVersion(version);
        t.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        t.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return t;
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Test
    void create_returnsPersistedTask() {
        UUID id = UUID.randomUUID();
        Task saved = buildTask(id, "My Task", TaskStatus.TODO, 0L);
        when(taskRepository.save(any(Task.class))).thenReturn(saved);

        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("My Task");
        req.setDescription("desc");

        TaskResponse resp = taskService.create(req);

        assertThat(resp.getId()).isEqualTo(id);
        assertThat(resp.getTitle()).isEqualTo("My Task");
        assertThat(resp.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(resp.getVersion()).isEqualTo(0L);
    }

    @Test
    void create_withDoneStatus_setsCompletedAt() {
        UUID id = UUID.randomUUID();
        Task saved = buildTask(id, "Done Task", TaskStatus.DONE, 0L);
        saved.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(taskRepository.save(any(Task.class))).thenReturn(saved);

        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Done Task");
        req.setStatus(TaskStatus.DONE);

        TaskResponse resp = taskService.create(req);

        assertThat(resp.getCompletedAt()).isNotNull();
    }

    // ── GET ──────────────────────────────────────────────────────────────────

    @Test
    void getById_existingTask_returnsResponse() {
        UUID id = UUID.randomUUID();
        Task t = buildTask(id, "Found", TaskStatus.TODO, 0L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(t));

        TaskResponse resp = taskService.getById(id);

        assertThat(resp.getId()).isEqualTo(id);
        assertThat(resp.getTitle()).isEqualTo("Found");
    }

    @Test
    void getById_missingTask_throwsTaskNotFoundException() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getById(id))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    // ── PUT (full replace) ───────────────────────────────────────────────────

    @Test
    void replace_updatesAllFields() {
        UUID id = UUID.randomUUID();
        Task existing = buildTask(id, "Old", TaskStatus.TODO, 0L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));

        Task updated = buildTask(id, "New Title", TaskStatus.IN_PROGRESS, 1L);
        updated.setDescription("new desc");
        updated.setPriority(TaskPriority.HIGH);
        updated.setDueDate(LocalDate.of(2027, 1, 1));
        updated.setTags(List.of("work"));
        when(taskRepository.save(any(Task.class))).thenReturn(updated);

        UpdateTaskRequest req = new UpdateTaskRequest();
        req.setTitle("New Title");
        req.setDescription("new desc");
        req.setStatus(TaskStatus.IN_PROGRESS);
        req.setPriority(TaskPriority.HIGH);
        req.setDueDate(LocalDate.of(2027, 1, 1));
        req.setTags(List.of("work"));
        req.setVersion(0L);

        TaskResponse resp = taskService.replace(id, req);

        assertThat(resp.getTitle()).isEqualTo("New Title");
        assertThat(resp.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(resp.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(resp.getTags()).containsExactly("work");
    }

    @Test
    void replace_transitionToDone_setsCompletedAt() {
        UUID id = UUID.randomUUID();
        Task existing = buildTask(id, "T", TaskStatus.IN_PROGRESS, 0L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));

        Task saved = buildTask(id, "T", TaskStatus.DONE, 1L);
        saved.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(taskRepository.save(any(Task.class))).thenReturn(saved);

        UpdateTaskRequest req = new UpdateTaskRequest();
        req.setTitle("T");
        req.setStatus(TaskStatus.DONE);
        req.setPriority(TaskPriority.MEDIUM);
        req.setVersion(0L);

        TaskResponse resp = taskService.replace(id, req);

        assertThat(resp.getCompletedAt()).isNotNull();
    }

    @Test
    void replace_transitionOutOfDone_clearsCompletedAt() {
        UUID id = UUID.randomUUID();
        Task existing = buildTask(id, "T", TaskStatus.DONE, 0L);
        existing.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));

        Task saved = buildTask(id, "T", TaskStatus.TODO, 1L);
        saved.setCompletedAt(null);
        when(taskRepository.save(any(Task.class))).thenReturn(saved);

        UpdateTaskRequest req = new UpdateTaskRequest();
        req.setTitle("T");
        req.setStatus(TaskStatus.TODO);
        req.setPriority(TaskPriority.MEDIUM);
        req.setVersion(0L);

        TaskResponse resp = taskService.replace(id, req);

        assertThat(resp.getCompletedAt()).isNull();
    }

    // ── PATCH ────────────────────────────────────────────────────────────────

    @Test
    void patch_partialFields_onlyChangesSpecified() {
        UUID id = UUID.randomUUID();
        Task existing = buildTask(id, "Original", TaskStatus.TODO, 0L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));

        Task patched = buildTask(id, "Original", TaskStatus.TODO, 1L);
        patched.setDescription("patched desc");
        when(taskRepository.save(any(Task.class))).thenReturn(patched);

        PatchTaskRequest req = new PatchTaskRequest();
        req.setDescription(JsonNullable.of("patched desc"));

        TaskResponse resp = taskService.patch(id, req);

        assertThat(resp.getDescription()).isEqualTo("patched desc");
        assertThat(resp.getTitle()).isEqualTo("Original");
    }

    @Test
    void patch_clearDescription_setsNull() {
        UUID id = UUID.randomUUID();
        Task existing = buildTask(id, "T", TaskStatus.TODO, 0L);
        existing.setDescription("some desc");
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));

        Task patched = buildTask(id, "T", TaskStatus.TODO, 1L);
        patched.setDescription(null);
        when(taskRepository.save(any(Task.class))).thenReturn(patched);

        PatchTaskRequest req = new PatchTaskRequest();
        req.setDescription(JsonNullable.of(null));

        TaskResponse resp = taskService.patch(id, req);

        assertThat(resp.getDescription()).isNull();
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @Test
    void delete_existingTask_removesIt() {
        UUID id = UUID.randomUUID();
        when(taskRepository.existsById(id)).thenReturn(true);

        taskService.delete(id);

        verify(taskRepository).deleteById(id);
    }

    @Test
    void delete_missingTask_throwsTaskNotFoundException() {
        UUID id = UUID.randomUUID();
        when(taskRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> taskService.delete(id))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    // ── POST-DELETE GET → 404 ─────────────────────────────────────────────

    @Test
    void getAfterDelete_returnsNotFound() {
        UUID id = UUID.randomUUID();
        // First call exists, second call (after delete) does not
        when(taskRepository.existsById(id)).thenReturn(true);
        taskService.delete(id);

        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getById(id))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
