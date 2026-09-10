package com.example.todo.dto;

import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import com.example.todo.validation.TagListDeserializer;
import com.example.todo.validation.ValidTag;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Request body for creating a new task")
public class CreateTaskRequest {

    @NotBlank(message = "title must not be blank")
    @Size(max = 200, message = "title must not exceed 200 characters")
    @Schema(description = "Task title", example = "Buy groceries", maxLength = 200)
    private String title;

    @Size(max = 4000, message = "description must not exceed 4000 characters")
    @Schema(description = "Task description", maxLength = 4000)
    private String description;

    @Schema(description = "Task status", example = "TODO")
    private TaskStatus status;

    @Schema(description = "Task priority", example = "MEDIUM")
    private TaskPriority priority;

    @Schema(description = "Due date in ISO-8601 format (yyyy-MM-dd)", example = "2026-12-31")
    private LocalDate dueDate;

    @Size(max = 10, message = "tags must not exceed 10 entries")
    @ValidTag
    @JsonDeserialize(using = TagListDeserializer.class)
    @Schema(description = "List of tags (max 10)", maxLength = 10)
    private List<String> tags;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
