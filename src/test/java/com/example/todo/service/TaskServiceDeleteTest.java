package com.example.todo.service;

import com.example.todo.domain.Task;
import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import com.example.todo.exception.TaskNotFoundException;
import com.example.todo.repository.TaskQueryRepository;
import com.example.todo.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceDeleteTest {

    @Mock TaskRepository taskRepository;
    @Mock TaskQueryRepository taskQueryRepository;
    @Mock TaskListQueryValidator queryValidator;
    @Mock TaskStatusTransitionValidator transitionValidator;

    TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskRepository, taskQueryRepository, queryValidator, transitionValidator);
    }

    private Task makeTask(UUID id) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("Test task");
        t.setStatus(TaskStatus.TODO);
        t.setPriority(TaskPriority.MEDIUM);
        t.setVersion(0L);
        t.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        t.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return t;
    }

    @Test
    void delete_existingTask_deletesById() {
        UUID id = UUID.randomUUID();
        when(taskRepository.existsById(id)).thenReturn(true);

        service.delete(id);

        verify(taskRepository).existsById(id);
        verify(taskRepository).deleteById(id);
    }

    @Test
    void delete_nonExistentTask_throwsTaskNotFoundException() {
        UUID id = UUID.randomUUID();
        when(taskRepository.existsById(id)).thenReturn(false);

        assertThrows(TaskNotFoundException.class, () -> service.delete(id));

        verify(taskRepository).existsById(id);
        verify(taskRepository, never()).deleteById(any());
    }

    @Test
    void delete_calledTwice_secondCallThrowsBecauseNotExists() {
        UUID id = UUID.randomUUID();
        // First call: exists
        when(taskRepository.existsById(id)).thenReturn(true).thenReturn(false);

        service.delete(id);
        assertThrows(TaskNotFoundException.class, () -> service.delete(id));

        verify(taskRepository, times(2)).existsById(id);
        verify(taskRepository, times(1)).deleteById(id);
    }
}
