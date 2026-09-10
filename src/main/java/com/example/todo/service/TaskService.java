package com.example.todo.service;

import com.example.todo.domain.Task;
import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import com.example.todo.dto.CreateTaskRequest;
import com.example.todo.dto.TaskResponse;
import com.example.todo.exception.TaskNotFoundException;
import com.example.todo.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public TaskResponse getById(UUID id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        return toResponse(task);
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest req) {
        Task task = new Task();
        task.setTitle(req.getTitle());
        task.setDescription(req.getDescription());

        TaskStatus status = req.getStatus() != null ? req.getStatus() : TaskStatus.TODO;
        task.setStatus(status);

        task.setPriority(req.getPriority() != null ? req.getPriority() : TaskPriority.MEDIUM);
        task.setDueDate(req.getDueDate());

        task.setTags(req.getTags() != null ? req.getTags() : new ArrayList<>());

        if (status == TaskStatus.DONE) {
            task.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }

        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    public static TaskResponse toResponse(Task task) {
        TaskResponse r = new TaskResponse();
        r.setId(task.getId());
        r.setTitle(task.getTitle());
        r.setDescription(task.getDescription());
        r.setStatus(task.getStatus());
        r.setPriority(task.getPriority());
        r.setDueDate(task.getDueDate());
        r.setCompletedAt(task.getCompletedAt());
        r.setTags(task.getTags() != null ? task.getTags() : new ArrayList<>());
        r.setVersion(task.getVersion());
        r.setCreatedAt(task.getCreatedAt());
        r.setUpdatedAt(task.getUpdatedAt());
        r.setOverdue(isOverdue(task));
        return r;
    }

    private static boolean isOverdue(Task task) {
        if (task.getDueDate() == null) return false;
        TaskStatus s = task.getStatus();
        if (s != TaskStatus.TODO && s != TaskStatus.IN_PROGRESS) return false;
        return task.getDueDate().isBefore(LocalDate.now(ZoneOffset.UTC));
    }
}
