package com.example.todo.service;

import com.example.todo.domain.Task;
import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import com.example.todo.dto.PatchTaskRequest;
import com.example.todo.dto.TaskResponse;
import com.example.todo.exception.EmptyPatchException;
import com.example.todo.exception.InvalidStatusTransitionException;
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
import org.openapitools.jackson.nullable.JsonNullable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServicePatchTest {

    @Mock TaskRepository taskRepository;
    @Mock TaskQueryRepository taskQueryRepository;
    @Mock TaskListQueryValidator queryValidator;
    @Mock TaskStatusTransitionValidator transitionValidator;

    TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskRepository, taskQueryRepository, queryValidator, transitionValidator);
    }

    private Task makeTask(UUID id, TaskStatus status, long version) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("Original title");
        t.setDescription("Original description");
        t.setStatus(status);
        t.setPriority(TaskPriority.MEDIUM);
        t.setVersion(version);
        t.setTags(new ArrayList<>(List.of("tag1")));
        t.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        t.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return t;
    }

    // --- AC: status DONE, completedAt set, other fields unchanged ---

    @Test
    void patch_statusToDone_setsCompletedAtAndReturns200() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.TODO, 0L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PatchTaskRequest req = new PatchTaskRequest();
        req.setStatus(JsonNullable.of(TaskStatus.DONE));
        req.setVersion(JsonNullable.of(0L));

        TaskResponse response = service.patch(id, req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        Task saved = captor.getValue();
        assertEquals(TaskStatus.DONE, saved.getStatus());
        assertNotNull(saved.getCompletedAt());
        assertEquals("Original title", saved.getTitle());
        assertEquals("Original description", saved.getDescription());
        assertEquals(TaskPriority.MEDIUM, saved.getPriority());
        assertEquals(List.of("tag1"), saved.getTags());
        assertNotNull(response);
    }

    // --- AC: dueDate: null clears it, other fields unchanged ---

    @Test
    void patch_dueDateNull_clearsDueDate() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.TODO, 0L);
        existing.setDueDate(LocalDate.of(2026, 12, 31));
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PatchTaskRequest req = new PatchTaskRequest();
        req.setDueDate(JsonNullable.of(null));

        service.patch(id, req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertNull(captor.getValue().getDueDate());
        assertEquals("Original title", captor.getValue().getTitle());
    }

    // --- AC: description: null clears it ---

    @Test
    void patch_descriptionNull_clearsDescription() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.TODO, 0L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PatchTaskRequest req = new PatchTaskRequest();
        req.setDescription(JsonNullable.of(null));

        service.patch(id, req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertNull(captor.getValue().getDescription());
    }

    // --- AC: tags omitted — existing tags unchanged ---

    @Test
    void patch_tagsAbsent_existingTagsUnchanged() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.TODO, 0L);
        existing.setTags(new ArrayList<>(List.of("existing-tag")));
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PatchTaskRequest req = new PatchTaskRequest();
        req.setTitle(JsonNullable.of("New title"));

        service.patch(id, req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertEquals(List.of("existing-tag"), captor.getValue().getTags());
    }

    // --- AC: tags: [] clears to empty list ---

    @Test
    void patch_tagsEmptyList_clearsAllTags() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.TODO, 0L);
        existing.setTags(new ArrayList<>(List.of("tag1", "tag2")));
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PatchTaskRequest req = new PatchTaskRequest();
        req.setTags(JsonNullable.of(new ArrayList<>()));

        service.patch(id, req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertTrue(captor.getValue().getTags().isEmpty());
    }

    // --- AC: version absent → last-write-wins, always succeeds ---

    @Test
    void patch_versionAbsent_succeedsRegardlessOfStoredVersion() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.TODO, 99L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PatchTaskRequest req = new PatchTaskRequest();
        req.setTitle(JsonNullable.of("Updated title"));

        assertDoesNotThrow(() -> service.patch(id, req));
        verify(taskRepository).save(any());
    }

    // --- AC: version present and mismatches → 409 VERSION_CONFLICT ---

    @Test
    void patch_versionMismatch_throwsVersionConflict() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.TODO, 1L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));

        PatchTaskRequest req = new PatchTaskRequest();
        req.setTitle(JsonNullable.of("Updated"));
        req.setVersion(JsonNullable.of(5L));

        assertThrows(VersionConflictException.class, () -> service.patch(id, req));
        verify(taskRepository, never()).save(any());
    }

    // --- AC: version present and matches → succeeds ---

    @Test
    void patch_versionMatches_succeeds() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.TODO, 3L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PatchTaskRequest req = new PatchTaskRequest();
        req.setTitle(JsonNullable.of("Updated"));
        req.setVersion(JsonNullable.of(3L));

        assertDoesNotThrow(() -> service.patch(id, req));
        verify(taskRepository).save(any());
    }

    // --- AC: CANCELLED → IN_PROGRESS is invalid transition → 409 ---

    @Test
    void patch_invalidStatusTransition_throwsInvalidStatusTransition() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.CANCELLED, 0L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));
        doThrow(new InvalidStatusTransitionException(TaskStatus.CANCELLED, TaskStatus.IN_PROGRESS))
                .when(transitionValidator).validate(TaskStatus.CANCELLED, TaskStatus.IN_PROGRESS);

        PatchTaskRequest req = new PatchTaskRequest();
        req.setStatus(JsonNullable.of(TaskStatus.IN_PROGRESS));

        assertThrows(InvalidStatusTransitionException.class, () -> service.patch(id, req));
        verify(taskRepository, never()).save(any());
    }

    // --- AC: non-existent task → 404 TASK_NOT_FOUND ---

    @Test
    void patch_taskNotFound_throwsTaskNotFound() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        PatchTaskRequest req = new PatchTaskRequest();
        req.setTitle(JsonNullable.of("Title"));

        assertThrows(TaskNotFoundException.class, () -> service.patch(id, req));
        verify(taskRepository, never()).save(any());
    }

    // --- AC: {} (all fields undefined) → EMPTY_PATCH ---

    @Test
    void patch_emptyBody_throwsEmptyPatch() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.TODO, 0L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));

        PatchTaskRequest req = new PatchTaskRequest();

        assertThrows(EmptyPatchException.class, () -> service.patch(id, req));
        verify(taskRepository, never()).save(any());
    }

    // --- AC: { "version": 5 } only → EMPTY_PATCH ---

    @Test
    void patch_versionOnlyBody_throwsEmptyPatch() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.TODO, 5L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));

        PatchTaskRequest req = new PatchTaskRequest();
        req.setVersion(JsonNullable.of(5L));

        assertThrows(EmptyPatchException.class, () -> service.patch(id, req));
        verify(taskRepository, never()).save(any());
    }

    // --- completedAt cleared when transitioning out of DONE ---

    @Test
    void patch_transitionOutOfDone_clearsCompletedAt() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.DONE, 0L);
        existing.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PatchTaskRequest req = new PatchTaskRequest();
        req.setStatus(JsonNullable.of(TaskStatus.TODO));

        service.patch(id, req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertNull(captor.getValue().getCompletedAt());
    }

    // --- already DONE, stays DONE → completedAt preserved ---

    @Test
    void patch_alreadyDoneStaysDone_completedAtPreserved() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.DONE, 0L);
        OffsetDateTime original = OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        existing.setCompletedAt(original);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PatchTaskRequest req = new PatchTaskRequest();
        req.setStatus(JsonNullable.of(TaskStatus.DONE));

        service.patch(id, req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertEquals(original, captor.getValue().getCompletedAt());
    }

    // --- transition validator called only when status is present ---

    @Test
    void patch_statusAbsent_transitionValidatorNotCalled() {
        UUID id = UUID.randomUUID();
        Task existing = makeTask(id, TaskStatus.TODO, 0L);
        when(taskRepository.findById(id)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PatchTaskRequest req = new PatchTaskRequest();
        req.setTitle(JsonNullable.of("Just updating title"));

        service.patch(id, req);

        verify(transitionValidator, never()).validate(any(), any());
    }
}
