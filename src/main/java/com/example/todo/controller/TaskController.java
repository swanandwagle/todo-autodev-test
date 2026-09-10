package com.example.todo.controller;

import com.example.todo.dto.CreateTaskRequest;
import com.example.todo.dto.TaskResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks", description = "Task management endpoints")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
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
}
