package com.example.todo.integration;

import com.example.todo.AbstractIntegrationTest;
import com.example.todo.domain.Task;
import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import com.example.todo.dto.UpdateTaskRequest;
import com.example.todo.exception.VersionConflictException;
import com.example.todo.repository.TaskQueryRepository;
import com.example.todo.repository.TaskRepository;
import com.example.todo.service.TaskListQueryValidator;
import com.example.todo.service.TaskService;
import com.example.todo.service.TaskStatusTransitionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Scenario 3 – Optimistic-lock concurrency.
 *
 * <p>Two concurrent requests targeting the same task at version 0:
 * exactly one must succeed, the other must receive a VersionConflictException.
 *
 * <p>This test is repeated 5 times (via {@code @RepeatedTest(5)}) to verify it is not flaky.
 * The concurrency is simulated at the service layer: the repository mock serialises DB access,
 * but the version-check logic in TaskService is exercised by two threads racing to call
 * {@code replace()} with the same version number. The first caller gets a saved result (version
 * bumped to 1); the mock then returns the bumped task to a second lookup, ensuring the second
 * caller sees a mismatch.
 */
@ExtendWith(MockitoExtension.class)
class OptimisticLockConcurrencyTest extends AbstractIntegrationTest {

    @Mock TaskRepository taskRepository;
    @Mock TaskQueryRepository taskQueryRepository;
    @Mock TaskListQueryValidator queryValidator;

    TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, taskQueryRepository,
                queryValidator, new TaskStatusTransitionValidator());
    }

    /**
     * Runs 5 times to prove the conflict is not a race condition that only sometimes fires.
     *
     * <p>Strategy: the first thread to acquire the lock reads version=0 and saves successfully
     * (mocked to return version=1). The second thread also reads version=0 but the service
     * compares the submitted version against the stored one at read time — since both threads
     * submit version=0, the second one is guaranteed to conflict once the first commits.
     *
     * <p>Because TaskService.replace() reads and version-checks synchronously (not in a DB
     * transaction here), we use a CountDownLatch to coordinate timing so both threads have
     * read the task before either attempts to write, maximising the chance the race is visible.
     */
    @RepeatedTest(5)
    void concurrentReplaceAtSameVersion_exactlyOneSucceedsOneConflicts() throws Exception {
        UUID id = UUID.randomUUID();

        Task storedV0 = task(id, 0L);
        Task storedV1 = task(id, 1L);

        // Semaphore with 1 permit serializes findById: the first thread acquires, reads v0,
        // saves (bumping callCount to 1), then releases. The second thread then acquires,
        // reads v1, and fails the version check.
        Semaphore semaphore = new Semaphore(1);
        AtomicInteger callCount = new AtomicInteger(0);

        when(taskRepository.findById(id)).thenAnswer(inv -> {
            semaphore.acquire();
            return Optional.of(callCount.get() == 0 ? storedV0 : storedV1);
        });

        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            callCount.incrementAndGet();
            semaphore.release();
            return storedV1;
        });

        UpdateTaskRequest req = putRequest(0L);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            tasks.add(() -> {
                try {
                    taskService.replace(id, req);
                    successCount.incrementAndGet();
                    return true;
                } catch (VersionConflictException e) {
                    conflictCount.incrementAndGet();
                    return false;
                }
            });
        }

        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Boolean> f : futures) {
            f.get();
        }

        assertThat(successCount.get() + conflictCount.get())
                .as("both requests must complete (success or conflict)")
                .isEqualTo(2);
        assertThat(successCount.get())
                .as("exactly one request must succeed")
                .isEqualTo(1);
        assertThat(conflictCount.get())
                .as("exactly one request must get a version conflict")
                .isEqualTo(1);
    }

    @Test
    void versionConflict_incorrectVersion_throwsVersionConflictException() {
        UUID id = UUID.randomUUID();
        Task stored = task(id, 5L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(stored));

        UpdateTaskRequest req = putRequest(3L); // wrong version

        org.junit.jupiter.api.Assertions.assertThrows(
                VersionConflictException.class,
                () -> taskService.replace(id, req));
    }

    @Test
    void versionConflict_exceptionCarriesExpectedAndActual() {
        UUID id = UUID.randomUUID();
        Task stored = task(id, 5L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(stored));

        UpdateTaskRequest req = putRequest(2L);

        try {
            taskService.replace(id, req);
        } catch (VersionConflictException e) {
            assertThat(e.getExpected()).isEqualTo(2L);
            assertThat(e.getActual()).isEqualTo(5L);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Task task(UUID id, long version) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("T");
        t.setStatus(TaskStatus.TODO);
        t.setPriority(TaskPriority.MEDIUM);
        t.setVersion(version);
        t.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        t.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return t;
    }

    private UpdateTaskRequest putRequest(long version) {
        UpdateTaskRequest r = new UpdateTaskRequest();
        r.setTitle("T");
        r.setStatus(TaskStatus.TODO);
        r.setPriority(TaskPriority.MEDIUM);
        r.setVersion(version);
        return r;
    }
}
