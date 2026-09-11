package com.example.todo.dto;

import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import com.example.todo.validation.ValidPatch;
import com.example.todo.validation.ValidTag;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

import java.time.LocalDate;
import java.util.List;

@ValidPatch
@Schema(description = "Request body for partial update of an existing task (JSON Merge Patch semantics)")
public class PatchTaskRequest {

    @Schema(description = "Task title (omit to keep current; cannot be set to null)", example = "Buy groceries")
    private JsonNullable<@Size(max = 200, message = "title must not exceed 200 characters") String> title
            = JsonNullable.undefined();

    @Schema(description = "Task description (omit to keep current; null clears it)", maxLength = 4000)
    private JsonNullable<@Size(max = 4000, message = "description must not exceed 4000 characters") String> description
            = JsonNullable.undefined();

    @Schema(description = "Task status (omit to keep current; cannot be set to null)", example = "IN_PROGRESS")
    private JsonNullable<TaskStatus> status = JsonNullable.undefined();

    @Schema(description = "Task priority (omit to keep current; cannot be set to null)", example = "HIGH")
    private JsonNullable<TaskPriority> priority = JsonNullable.undefined();

    @Schema(description = "Due date in ISO-8601 format (omit to keep current; null clears it)", example = "2026-12-31")
    private JsonNullable<LocalDate> dueDate = JsonNullable.undefined();

    @Schema(description = "Tags list (omit to keep current; [] clears all tags)", maxLength = 10)
    private JsonNullable<@Size(max = 10, message = "tags must not exceed 10 entries") @ValidTag List<String>> tags
            = JsonNullable.undefined();

    @Schema(description = "Optimistic lock version (omit for last-write-wins; if present must match stored version)", example = "1")
    private JsonNullable<Long> version = JsonNullable.undefined();

    public JsonNullable<String> getTitle() { return title; }
    public void setTitle(JsonNullable<String> title) { this.title = title; }

    public JsonNullable<String> getDescription() { return description; }
    public void setDescription(JsonNullable<String> description) { this.description = description; }

    public JsonNullable<TaskStatus> getStatus() { return status; }
    public void setStatus(JsonNullable<TaskStatus> status) { this.status = status; }

    public JsonNullable<TaskPriority> getPriority() { return priority; }
    public void setPriority(JsonNullable<TaskPriority> priority) { this.priority = priority; }

    public JsonNullable<LocalDate> getDueDate() { return dueDate; }
    public void setDueDate(JsonNullable<LocalDate> dueDate) { this.dueDate = dueDate; }

    public JsonNullable<List<String>> getTags() { return tags; }

    @JsonDeserialize(using = NullableTagListDeserializer.class)
    public void setTags(JsonNullable<List<String>> tags) { this.tags = tags; }

    public JsonNullable<Long> getVersion() { return version; }
    public void setVersion(JsonNullable<Long> version) { this.version = version; }
}
