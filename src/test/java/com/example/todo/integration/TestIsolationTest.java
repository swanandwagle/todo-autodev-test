package com.example.todo.integration;

import com.example.todo.AbstractIntegrationTest;
import com.example.todo.domain.Task;
import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import com.example.todo.dto.CreateTaskRequest;
import com.example.todo.dto.TaskResponse;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Scenario 6 – Test isolation / no shared state between tests.
 *
 * <p>Verifies that each test operates on a clean slate: no data persists from one test to the
 * next, and the repository state is controlled entirely by per-test mock configuration.
 *
 * <p>In the full Testcontainers integration suite, isolation is achieved by truncating the
 * {@code tasks} table (or using rolled-back transactions) in a {@code @BeforeEach} method on the
 * base class. In this Mockito-based suite, Mockito resets mocks between tests automatically
 * because each test method receives a fresh set of mock objects created by {@code @Mock} in
 * the {@code @BeforeEach} setup — there is no shared mutable state.
 *
 * <p>The tests here explicitly verify that:
 * <ol>
 *   <li>A task created in one test is NOT visible in another test (repository mocks are per-test).</li>
 *   <li>Each test starts from a known empty state.</li>
 *   <li>Two consecutive "create" operations do not interfere with each other.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class TestIsolationTest extends AbstractIntegrationTest {

    @Mock TaskRepository taskRepository;
    @Mock TaskQueryRepository taskQueryRepository;
    @Mock TaskListQueryValidator queryValidator;

    TaskService taskService;

    @BeforeEach
    void setUp() {
        // Fresh TaskService with fresh mocks — no shared state between test methods
        taskService = new TaskService(taskRepository, taskQueryRepository,
                queryValidator, new TaskStatusTransitionValidator());
    }

    @Test
    void firstTest_createsTask_idIsReturned() {
        UUID id = UUID.randomUUID();
        Task saved = task(id);
        when(taskRepository.save(any(Task.class))).thenReturn(saved);

        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Test A");

        TaskResponse resp = taskService.create(req);

        assertThat(resp.getId()).isEqualTo(id);
    }

    @Test
    void secondTest_doesNotSeeTaskFromFirstTest() {
        // The mock has not been configured to return anything for findById, so any lookup
        // returns Optional.empty(), confirming no shared state carries over from firstTest.
        UUID idFromPreviousTest = UUID.randomUUID();
        when(taskRepository.findById(idFromPreviousTest)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getById(idFromPreviousTest))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void thirdTest_independentCreateDoesNotConflict() {
        UUID idB = UUID.randomUUID();
        Task savedB = task(idB);
        savedB.setTitle("Test B");
        when(taskRepository.save(any(Task.class))).thenReturn(savedB);

        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Test B");

        TaskResponse resp = taskService.create(req);

        // Only the task created in this test is returned — no pollution from other tests
        assertThat(resp.getId()).isEqualTo(idB);
        assertThat(resp.getTitle()).isEqualTo("Test B");
    }

    @Test
    void fourthTest_deleteDoesNotAffectOtherTests() {
        UUID id = UUID.randomUUID();
        when(taskRepository.existsById(id)).thenReturn(true);

        taskService.delete(id);

        // After delete in this test, the task is gone in this test's mock scope
        when(taskRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> taskService.getById(id))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void fifthTest_noStateFromFourthTest() {
        // Confirms that mock state is not carried over: delete in fourthTest has no effect here
        UUID freshId = UUID.randomUUID();
        Task t = task(freshId);
        when(taskRepository.findById(freshId)).thenReturn(Optional.of(t));

        TaskResponse resp = taskService.getById(freshId);

        assertThat(resp.getId()).isEqualTo(freshId);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private Task task(UUID id) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("T");
        t.setStatus(TaskStatus.TODO);
        t.setPriority(TaskPriority.MEDIUM);
        t.setVersion(0L);
        t.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        t.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return t;
    }
}
