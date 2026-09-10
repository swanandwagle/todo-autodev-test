package com.example.todo.service;

import com.example.todo.domain.Task;
import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import com.example.todo.dto.CreateTaskRequest;
import com.example.todo.dto.PageResponse;
import com.example.todo.dto.TaskListQuery;
import com.example.todo.dto.TaskResponse;
import com.example.todo.exception.TaskNotFoundException;
import com.example.todo.repository.TaskQueryRepository;
import com.example.todo.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
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
    private final TaskQueryRepository taskQueryRepository;
    private final TaskListQueryValidator queryValidator;

    public TaskService(TaskRepository taskRepository,
                       TaskQueryRepository taskQueryRepository,
                       TaskListQueryValidator queryValidator) {
        this.taskRepository = taskRepository;
        this.taskQueryRepository = taskQueryRepository;
        this.queryValidator = queryValidator;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> listTasks(TaskListQuery query) {
        queryValidator.validate(query);

        Specification<Task> spec = buildSpecification(query);
        Page<Task> page = taskQueryRepository.findAll(spec, query);
        return PageResponse.from(page, TaskService::toResponse);
    }

    private static final Specification<Task> ALWAYS_TRUE = (root, query, cb) -> cb.conjunction();

    private Specification<Task> buildSpecification(TaskListQuery q) {
        Specification<Task> spec = ALWAYS_TRUE;

        if (q.getStatus() != null && !q.getStatus().isEmpty()) {
            spec = spec.and(TaskSpecifications.hasStatuses(q.getStatus()));
        }
        if (q.getPriority() != null && !q.getPriority().isEmpty()) {
            spec = spec.and(TaskSpecifications.hasPriorities(q.getPriority()));
        }
        if (q.getDueFrom() != null) {
            spec = spec.and(TaskSpecifications.dueDateFrom(q.getDueFrom()));
        }
        if (q.getDueTo() != null) {
            spec = spec.and(TaskSpecifications.dueDateTo(q.getDueTo()));
        }
        if (q.getHasDueDate() != null) {
            spec = spec.and(TaskSpecifications.hasDueDate(q.getHasDueDate()));
        }
        if (Boolean.TRUE.equals(q.getOverdue())) {
            spec = spec.and(TaskSpecifications.isOverdue(LocalDate.now(ZoneOffset.UTC)));
        }
        if (q.getTags() != null && !q.getTags().isEmpty()) {
            spec = spec.and(TaskSpecifications.tagsContainAll(q.getTags()));
        }
        if (q.getTagsAny() != null && !q.getTagsAny().isEmpty()) {
            spec = spec.and(TaskSpecifications.tagsOverlapAny(q.getTagsAny()));
        }
        if (q.getQ() != null && !q.getQ().isBlank()) {
            spec = spec.and(TaskSpecifications.fullTextSearch(q.getQ()));
        }
        if (q.getUpdatedSince() != null) {
            spec = spec.and(TaskSpecifications.updatedSince(q.getUpdatedSince()));
        }

        return spec;
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
