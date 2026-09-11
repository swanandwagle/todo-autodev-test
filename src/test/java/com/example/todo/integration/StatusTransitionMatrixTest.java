package com.example.todo.integration;

import com.example.todo.AbstractIntegrationTest;
import com.example.todo.domain.Task;
import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import com.example.todo.dto.UpdateTaskRequest;
import com.example.todo.exception.InvalidStatusTransitionException;
import com.example.todo.repository.TaskQueryRepository;
import com.example.todo.repository.TaskRepository;
import com.example.todo.service.TaskListQueryValidator;
import com.example.todo.service.TaskService;
import com.example.todo.service.TaskStatusTransitionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Scenario 2 – Status-transition matrix (16 combinations: 4×4).
 *
 * <p>Expected outcomes (from TaskStatusTransitionValidator):
 * <pre>
 *  FROM \ TO      TODO   IN_PROGRESS   DONE   CANCELLED
 *  TODO           ok     ok            ok     ok
 *  IN_PROGRESS    ok     ok            ok     ok
 *  DONE           ok     ok            ok     INVALID
 *  CANCELLED      INVALID INVALID      INVALID INVALID (self only)
 * </pre>
 * Wait – CANCELLED→CANCELLED is allowed (self-transition). All other CANCELLED→* are invalid.
 */
@ExtendWith(MockitoExtension.class)
class StatusTransitionMatrixTest extends AbstractIntegrationTest {

    @Mock TaskRepository taskRepository;
    @Mock TaskQueryRepository taskQueryRepository;
    @Mock TaskListQueryValidator queryValidator;

    TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, taskQueryRepository,
                queryValidator, new TaskStatusTransitionValidator());
    }

    static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(TaskStatus.TODO,        TaskStatus.TODO),
                Arguments.of(TaskStatus.TODO,        TaskStatus.IN_PROGRESS),
                Arguments.of(TaskStatus.TODO,        TaskStatus.DONE),
                Arguments.of(TaskStatus.TODO,        TaskStatus.CANCELLED),
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.TODO),
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.IN_PROGRESS),
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.DONE),
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
                Arguments.of(TaskStatus.DONE,        TaskStatus.TODO),
                Arguments.of(TaskStatus.DONE,        TaskStatus.IN_PROGRESS),
                Arguments.of(TaskStatus.DONE,        TaskStatus.DONE),
                Arguments.of(TaskStatus.CANCELLED,   TaskStatus.CANCELLED)
        );
    }

    static Stream<Arguments> forbiddenTransitions() {
        return Stream.of(
                Arguments.of(TaskStatus.DONE,      TaskStatus.CANCELLED),
                Arguments.of(TaskStatus.CANCELLED, TaskStatus.TODO),
                Arguments.of(TaskStatus.CANCELLED, TaskStatus.IN_PROGRESS),
                Arguments.of(TaskStatus.CANCELLED, TaskStatus.DONE)
        );
    }

    @ParameterizedTest(name = "{0} -> {1} should succeed")
    @MethodSource("allowedTransitions")
    void allowedTransition_doesNotThrow(TaskStatus from, TaskStatus to) {
        UUID id = UUID.randomUUID();
        Task existing = task(id, from);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));

        Task saved = task(id, to);
        if (to == TaskStatus.DONE) saved.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(taskRepository.save(any(Task.class))).thenReturn(saved);

        UpdateTaskRequest req = putRequest(to, 0L);

        assertThatCode(() -> taskService.replace(id, req)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} -> {1} should be rejected")
    @MethodSource("forbiddenTransitions")
    void forbiddenTransition_throwsInvalidStatusTransitionException(TaskStatus from, TaskStatus to) {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.of(task(id, from)));

        UpdateTaskRequest req = putRequest(to, 0L);

        assertThatThrownBy(() -> taskService.replace(id, req))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Task task(UUID id, TaskStatus status) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("T");
        t.setStatus(status);
        t.setPriority(TaskPriority.MEDIUM);
        t.setVersion(0L);
        t.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        t.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (status == TaskStatus.DONE) t.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return t;
    }

    private UpdateTaskRequest putRequest(TaskStatus to, long version) {
        UpdateTaskRequest r = new UpdateTaskRequest();
        r.setTitle("T");
        r.setStatus(to);
        r.setPriority(TaskPriority.MEDIUM);
        r.setVersion(version);
        return r;
    }
}
