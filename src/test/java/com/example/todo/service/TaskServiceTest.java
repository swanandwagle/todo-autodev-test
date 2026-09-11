package com.example.todo.service;

import com.example.todo.domain.Task;
import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import com.example.todo.dto.CreateTaskRequest;
import com.example.todo.dto.PageResponse;
import com.example.todo.dto.TaskListQuery;
import com.example.todo.dto.TaskResponse;
import com.example.todo.dto.UpdateTaskRequest;
import com.example.todo.exception.TaskNotFoundException;
import com.example.todo.exception.VersionConflictException;
import com.example.todo.repository.TaskQueryRepository;
import com.example.todo.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock TaskQueryRepository taskQueryRepository;
    @Mock TaskListQueryValidator queryValidator;
    @Mock TaskStatusTransitionValidator transitionValidator;

    TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskRepository, taskQueryRepository, queryValidator, transitionValidator);
    }

    // --- helpers ---

    private Task makeTask(UUID id, TaskStatus status, long version) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("Test task");
        t.setStatus(status);
        t.setPriority(TaskPriority.MEDIUM);
        t.setVersion(version);
        t.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        t.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return t;
    }

    // --- listTasks ---

    @Test
    void listTasks_callsValidatorAndReturnsPage() {
        TaskListQuery query = new TaskListQuery();
        Task task = makeTask(UUID.randomUUID(), TaskStatus.TODO, 0L);
        Page<Task> page = new PageImpl<>(List.of(task));
        when(taskQueryRepository.findAll(any(Specification.class), eq(query))).thenReturn(page);

        PageResponse<TaskResponse> result = service.listTasks(query);

        verify(queryValidator).validate(query);
        assertEquals(1, result.getItems().size());
        assertEquals("Test task", result.getItems().get(0).getTitle());
    }

    // --- getById ---

    @Test
    void getById_found_returnsResponse() {
        UUID id = UUID.randomUUID();
        Task task = makeTask(id, TaskStatus.TODO, 0L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(task));

        TaskResponse response = service.getById(id);

        assertEquals(id, response.getId());
        assertEquals(TaskStatus.TODO, response.getStatus());
    }

    @Test
    void getById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> service.getById(id));
    }

    // --- create ---

    @Test
    void create_defaultStatusAndPriority() {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("New task");

        Task saved = makeTask(UUID.randomUUID(), TaskStatus.TODO, 0L);
        when(taskRepository.save(any())).thenReturn(saved);

        TaskResponse response = service.create(req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertEquals(TaskStatus.TODO, captor.getValue().getStatus());
        assertEquals(TaskPriority.MEDIUM, captor.getValue().getPriority());
        assertNotNull(response);
    }

    @Test
    void create_explicitStatus_usedAsIs() {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Done task");
        req.setStatus(TaskStatus.DONE);

        Task saved = makeTask(UUID.randomUUID(), TaskStatus.DONE, 0L);
        saved.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(taskRepository.save(any())).thenReturn(saved);

        service.create(req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertEquals(TaskStatus.DONE, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getCompletedAt());
    }

    @Test
    void create_statusNotDone_completedAtNotSet() {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("In progress task");
        req.setStatus(TaskStatus.IN_PROGRESS);

        Task saved = makeTask(UUID.randomUUID(), TaskStatus.IN_PROGRESS, 0L);
        when(taskRepository.save(any())).thenReturn(saved);

        service.create(req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertNull(captor.getValue().getCompletedAt());
    }

    @Test
    void create_withTags_savedToTask() {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Tagged task");
        req.setTags(List.of("urgent", "work"));

        Task saved = makeTask(UUID.randomUUID(), TaskStatus.TODO, 0L);
        when(taskRepository.save(any())).thenReturn(saved);

        service.create(req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertEquals(List.of("urgent", "work"), captor.getValue().getTags());
    }

    // --- replace ---

    @Test
    void replace_versionMismatch_throws() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.TODO, 1L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));

        UpdateTaskRequest req = new UpdateTaskRequest();
        req.setTitle("Updated");
        req.setStatus(TaskStatus.TODO);
        req.setPriority(TaskPriority.HIGH);
        req.setVersion(99L);

        assertThrows(VersionConflictException.class, () -> service.replace(id, req));
        verify(transitionValidator, never()).validate(any(), any());
    }

    @Test
    void replace_taskNotFound_throws() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        UpdateTaskRequest req = new UpdateTaskRequest();
        req.setTitle("Title");
        req.setStatus(TaskStatus.TODO);
        req.setPriority(TaskPriority.MEDIUM);
        req.setVersion(0L);

        assertThrows(TaskNotFoundException.class, () -> service.replace(id, req));
    }

    @Test
    void replace_transitionToInProgress_setsFields() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.TODO, 0L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));

        Task saved = makeTask(id, TaskStatus.IN_PROGRESS, 1L);
        when(taskRepository.save(any())).thenReturn(saved);

        UpdateTaskRequest req = new UpdateTaskRequest();
        req.setTitle("Updated title");
        req.setStatus(TaskStatus.IN_PROGRESS);
        req.setPriority(TaskPriority.HIGH);
        req.setVersion(0L);

        TaskResponse response = service.replace(id, req);

        verify(transitionValidator).validate(TaskStatus.TODO, TaskStatus.IN_PROGRESS);
        assertNotNull(response);
    }

    @Test
    void replace_transitionToDone_setsCompletedAt() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.IN_PROGRESS, 0L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));

        Task saved = makeTask(id, TaskStatus.DONE, 1L);
        saved.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(taskRepository.save(any())).thenReturn(saved);

        UpdateTaskRequest req = new UpdateTaskRequest();
        req.setTitle("Done");
        req.setStatus(TaskStatus.DONE);
        req.setPriority(TaskPriority.MEDIUM);
        req.setVersion(0L);

        service.replace(id, req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertNotNull(captor.getValue().getCompletedAt());
    }

    @Test
    void replace_alreadyDoneStaysDone_completedAtPreserved() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.DONE, 0L);
        OffsetDateTime originalCompletion = OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        existing.setCompletedAt(originalCompletion);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));

        Task saved = makeTask(id, TaskStatus.DONE, 1L);
        saved.setCompletedAt(originalCompletion);
        when(taskRepository.save(any())).thenReturn(saved);

        UpdateTaskRequest req = new UpdateTaskRequest();
        req.setTitle("Done again");
        req.setStatus(TaskStatus.DONE);
        req.setPriority(TaskPriority.MEDIUM);
        req.setVersion(0L);

        service.replace(id, req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        // completedAt should NOT be overwritten when DONE -> DONE
        assertEquals(originalCompletion, captor.getValue().getCompletedAt());
    }

    @Test
    void replace_transitionFromDoneToTodo_clearsCompletedAt() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.DONE, 0L);
        existing.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));

        Task saved = makeTask(id, TaskStatus.TODO, 1L);
        when(taskRepository.save(any())).thenReturn(saved);

        UpdateTaskRequest req = new UpdateTaskRequest();
        req.setTitle("Reopened");
        req.setStatus(TaskStatus.TODO);
        req.setPriority(TaskPriority.LOW);
        req.setVersion(0L);

        service.replace(id, req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertNull(captor.getValue().getCompletedAt());
    }

    // --- toResponse / isOverdue ---

    @Test
    void toResponse_overdueTask_flaggedTrue() {
        Task task = makeTask(UUID.randomUUID(), TaskStatus.TODO, 0L);
        task.setDueDate(LocalDate.now(ZoneOffset.UTC).minusDays(1));

        TaskResponse response = TaskService.toResponse(task);

        assertTrue(response.isOverdue());
    }

    @Test
    void toResponse_futureDueDate_notOverdue() {
        Task task = makeTask(UUID.randomUUID(), TaskStatus.TODO, 0L);
        task.setDueDate(LocalDate.now(ZoneOffset.UTC).plusDays(1));

        TaskResponse response = TaskService.toResponse(task);

        assertFalse(response.isOverdue());
    }

    @Test
    void toResponse_doneTaskWithPastDueDate_notOverdue() {
        Task task = makeTask(UUID.randomUUID(), TaskStatus.DONE, 0L);
        task.setDueDate(LocalDate.now(ZoneOffset.UTC).minusDays(1));

        TaskResponse response = TaskService.toResponse(task);

        assertFalse(response.isOverdue());
    }

    @Test
    void toResponse_noDueDate_notOverdue() {
        Task task = makeTask(UUID.randomUUID(), TaskStatus.TODO, 0L);

        TaskResponse response = TaskService.toResponse(task);

        assertFalse(response.isOverdue());
    }
}
