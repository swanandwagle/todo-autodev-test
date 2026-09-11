package com.example.todo.controller;

import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import com.example.todo.dto.CreateTaskRequest;
import com.example.todo.dto.PageResponse;
import com.example.todo.dto.PatchTaskRequest;
import com.example.todo.dto.TaskListQuery;
import com.example.todo.dto.TaskResponse;
import com.example.todo.dto.UpdateTaskRequest;
import com.example.todo.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks", description = "Task management endpoints")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @Operation(summary = "List tasks with optional filtering, searching and pagination")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated list of tasks",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid query parameters",
                content = @Content(mediaType = "application/problem+json"))
    })
    @GetMapping(produces = "application/json")
    public ResponseEntity<PageResponse<TaskResponse>> listTasks(
            @Parameter(description = "Filter by status (comma-separated or repeated)")
            @RequestParam(required = false) List<TaskStatus> status,
            @Parameter(description = "Filter by priority (comma-separated or repeated)")
            @RequestParam(required = false) List<TaskPriority> priority,
            @Parameter(description = "Inclusive lower bound for dueDate (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @Parameter(description = "Inclusive upper bound for dueDate (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @Parameter(description = "Filter by whether a dueDate is set")
            @RequestParam(required = false) Boolean hasDueDate,
            @Parameter(description = "When true, return only overdue tasks")
            @RequestParam(required = false) Boolean overdue,
            @Parameter(description = "Return only tasks containing ALL listed tags (comma-separated)")
            @RequestParam(required = false) List<String> tags,
            @Parameter(description = "Return only tasks containing ANY listed tag (comma-separated)")
            @RequestParam(required = false) List<String> tagsAny,
            @Parameter(description = "Full-text search term matched against title and description")
            @RequestParam(required = false) String q,
            @Parameter(description = "Return only tasks updated at or after this instant (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime updatedSince,
            @Parameter(description = "Page number, 0-based (default 0)")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size, 1–100 (default 20)")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort field: dueDate, createdAt, updatedAt, priority, title, status")
            @RequestParam(required = false) String sort) {

        TaskListQuery listQuery = new TaskListQuery();
        listQuery.setStatus(status);
        listQuery.setPriority(priority);
        listQuery.setDueFrom(dueFrom);
        listQuery.setDueTo(dueTo);
        listQuery.setHasDueDate(hasDueDate);
        listQuery.setOverdue(overdue);
        listQuery.setTags(tags);
        listQuery.setTagsAny(tagsAny);
        listQuery.setQ(q);
        listQuery.setUpdatedSince(updatedSince);
        listQuery.setPage(page);
        listQuery.setSize(size);

        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            listQuery.setSortField(parts[0].trim());
            listQuery.setSortDir(parts.length > 1 ? parts[1].trim() : "asc");
        }

        return ResponseEntity.ok(taskService.listTasks(listQuery));
    }

    @Operation(summary = "Get a task by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Task found",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = TaskResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid ID format",
                content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(responseCode = "404", description = "Task not found",
                content = @Content(mediaType = "application/problem+json"))
    })
    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<TaskResponse> getById(
            @Parameter(description = "Task UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(taskService.getById(id));
    }

    @Operation(summary = "Create a new task")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Task created",
                headers = @Header(name = "Location", description = "URL of the created task",
                        schema = @Schema(type = "string"))),
        @ApiResponse(responseCode = "400", description = "Validation failed or malformed request",
                content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(responseCode = "413", description = "Payload too large",
                content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(responseCode = "415", description = "Unsupported media type",
                content = @Content(mediaType = "application/problem+json"))
    })
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request,
                                               UriComponentsBuilder ucb) {
        TaskResponse response = taskService.create(request);
        URI location = ucb.path("/api/v1/tasks/{id}").buildAndExpand(response.getId()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Partially update a task (JSON Merge Patch)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Task partially updated",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = TaskResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed, empty patch, or malformed request",
                content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(responseCode = "404", description = "Task not found",
                content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(responseCode = "409", description = "Version conflict or invalid status transition",
                content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(responseCode = "413", description = "Payload too large",
                content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(responseCode = "415", description = "Unsupported media type",
                content = @Content(mediaType = "application/problem+json"))
    })
    @PatchMapping(value = "/{id}",
            consumes = {"application/json", "application/merge-patch+json"},
            produces = "application/json")
    public ResponseEntity<TaskResponse> patch(
            @Parameter(description = "Task UUID") @PathVariable UUID id,
            @Valid @RequestBody PatchTaskRequest request) {
        return ResponseEntity.ok(taskService.patch(id, request));
    }

    @Operation(summary = "Replace a task (full update)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Task replaced",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = TaskResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed or malformed request",
                content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(responseCode = "404", description = "Task not found",
                content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(responseCode = "409", description = "Version conflict or invalid status transition",
                content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(responseCode = "413", description = "Payload too large",
                content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(responseCode = "415", description = "Unsupported media type",
                content = @Content(mediaType = "application/problem+json"))
    })
    @PutMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<TaskResponse> replace(
            @Parameter(description = "Task UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(taskService.replace(id, request));
    }

    @Operation(summary = "Delete a task by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Task deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid ID format",
                content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(responseCode = "404", description = "Task not found",
                content = @Content(mediaType = "application/problem+json"))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Task UUID") @PathVariable UUID id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
