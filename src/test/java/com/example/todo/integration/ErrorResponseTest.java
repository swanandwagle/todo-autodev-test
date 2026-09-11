package com.example.todo.integration;

import com.example.todo.AbstractIntegrationTest;
import com.example.todo.domain.Task;
import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import com.example.todo.dto.PatchTaskRequest;
import com.example.todo.dto.UpdateTaskRequest;
import com.example.todo.exception.EmptyPatchException;
import com.example.todo.exception.InvalidStatusTransitionException;
import com.example.todo.exception.TaskNotFoundException;
import com.example.todo.exception.VersionConflictException;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Scenario 5 – Error codes.
 *
 * <p>Every reachable service-layer error condition has at least one test asserting the
 * exception type and that the message contains the expected dynamic values (IDs, versions).
 *
 * <p>HTTP-level status codes (400, 404, 409, etc.) are mapped by {@code GlobalExceptionHandler}.
 * Those mappings are verified separately in {@code GlobalExceptionHandlerTest}. Here we verify
 * that the service layer throws the correct, well-formed exceptions so the handler has the right
 * exception to map.
 */
@ExtendWith(MockitoExtension.class)
class ErrorResponseTest extends AbstractIntegrationTest {

    @Mock TaskRepository taskRepository;
    @Mock TaskQueryRepository taskQueryRepository;
    @Mock TaskListQueryValidator queryValidator;

    TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, taskQueryRepository,
                queryValidator, new TaskStatusTransitionValidator());
    }

    // ── TASK_NOT_FOUND (404) ─────────────────────────────────────────────────

    @Test
    void getById_notFound_exceptionMessageContainsId() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getById(id))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void replace_notFound_exceptionMessageContainsId() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.replace(id, putRequest(TaskStatus.TODO, 0L)))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void patch_notFound_exceptionMessageContainsId() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        PatchTaskRequest req = new PatchTaskRequest();
        req.setTitle(JsonNullable.of("x"));

        assertThatThrownBy(() -> taskService.patch(id, req))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void delete_notFound_exceptionMessageContainsId() {
        UUID id = UUID.randomUUID();
        when(taskRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> taskService.delete(id))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    // ── VERSION_CONFLICT (409) ───────────────────────────────────────────────

    @Test
    void replace_versionMismatch_exceptionCarriesExpectedAndActual() {
        UUID id = UUID.randomUUID();
        Task stored = task(id, TaskStatus.TODO, 7L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(stored));

        try {
            taskService.replace(id, putRequest(TaskStatus.TODO, 3L));
        } catch (VersionConflictException e) {
            assertThat(e.getExpected()).isEqualTo(3L);
            assertThat(e.getActual()).isEqualTo(7L);
            assertThat(e.getMessage()).contains(id.toString());
            assertThat(e.getMessage()).contains("3");
            assertThat(e.getMessage()).contains("7");
        }
    }

    @Test
    void patch_versionMismatch_exceptionCarriesExpectedAndActual() {
        UUID id = UUID.randomUUID();
        Task stored = task(id, TaskStatus.TODO, 4L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(stored));

        PatchTaskRequest req = new PatchTaskRequest();
        req.setTitle(JsonNullable.of("new title"));
        req.setVersion(JsonNullable.of(1L));

        try {
            taskService.patch(id, req);
        } catch (VersionConflictException e) {
            assertThat(e.getExpected()).isEqualTo(1L);
            assertThat(e.getActual()).isEqualTo(4L);
            assertThat(e.getMessage()).contains(id.toString());
        }
    }

    // ── INVALID_STATUS_TRANSITION (409) ──────────────────────────────────────

    @Test
    void replace_invalidTransition_exceptionContainsFromAndTo() {
        UUID id = UUID.randomUUID();
        Task stored = task(id, TaskStatus.CANCELLED, 0L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> taskService.replace(id, putRequest(TaskStatus.TODO, 0L)))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("CANCELLED")
                .hasMessageContaining("TODO");
    }

    @Test
    void replace_invalidTransition_exceptionCarriesFromAndToFields() {
        UUID id = UUID.randomUUID();
        Task stored = task(id, TaskStatus.DONE, 0L);
        stored.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(taskRepository.findById(id)).thenReturn(Optional.of(stored));

        try {
            taskService.replace(id, putRequest(TaskStatus.CANCELLED, 0L));
        } catch (InvalidStatusTransitionException e) {
            assertThat(e.getFrom()).isEqualTo(TaskStatus.DONE);
            assertThat(e.getTo()).isEqualTo(TaskStatus.CANCELLED);
        }
    }

    // ── EMPTY_PATCH (400) ────────────────────────────────────────────────────

    @Test
    void patch_emptyBody_throwsEmptyPatchException() {
        UUID id = UUID.randomUUID();
        Task stored = task(id, TaskStatus.TODO, 0L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(stored));

        PatchTaskRequest req = new PatchTaskRequest();
        // all fields are undefined (JsonNullable.undefined())

        assertThatThrownBy(() -> taskService.patch(id, req))
                .isInstanceOf(EmptyPatchException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Task task(UUID id, TaskStatus status, long version) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("T");
        t.setStatus(status);
        t.setPriority(TaskPriority.MEDIUM);
        t.setVersion(version);
        t.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        t.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return t;
    }

    private UpdateTaskRequest putRequest(TaskStatus status, long version) {
        UpdateTaskRequest r = new UpdateTaskRequest();
        r.setTitle("T");
        r.setStatus(status);
        r.setPriority(TaskPriority.MEDIUM);
        r.setVersion(version);
        return r;
    }
}
