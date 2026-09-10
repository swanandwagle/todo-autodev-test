package com.example.todo.dto;

import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Binds all GET /api/v1/tasks query parameters. Validation is performed in
 * TaskListQueryValidator before reaching the service layer.
 */
public class TaskListQuery {

    @Parameter(description = "Filter by one or more statuses (comma-separated or repeated)")
    private List<TaskStatus> status;

    @Parameter(description = "Filter by one or more priorities (comma-separated or repeated)")
    private List<TaskPriority> priority;

    @Parameter(description = "Inclusive lower bound for dueDate (yyyy-MM-dd)")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueFrom;

    @Parameter(description = "Inclusive upper bound for dueDate (yyyy-MM-dd)")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueTo;

    @Parameter(description = "When true, return only tasks that have a dueDate set; when false, only tasks without one")
    private Boolean hasDueDate;

    @Parameter(description = "When true, return only overdue tasks (dueDate < today AND status in TODO/IN_PROGRESS)")
    private Boolean overdue;

    @Parameter(description = "Return only tasks that contain ALL of the given tags (comma-separated)")
    private List<String> tags;

    @Parameter(description = "Return only tasks that contain ANY of the given tags (comma-separated)")
    private List<String> tagsAny;

    @Parameter(description = "Full-text search term matched against title and description")
    private String q;

    @Parameter(description = "Return only tasks updated at or after this instant (ISO-8601)")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime updatedSince;

    @Parameter(description = "Page number, 0-based (default 0)")
    private Integer page;

    @Parameter(description = "Page size, 1–100 (default 20)")
    private Integer size;

    @Parameter(description = "Sort field (dueDate, createdAt, updatedAt, priority, title, status)")
    private String sortField;

    @Parameter(description = "Sort direction: asc or desc (default asc)")
    private String sortDir;

    public List<TaskStatus> getStatus() { return status; }
    public void setStatus(List<TaskStatus> status) { this.status = status; }

    public List<TaskPriority> getPriority() { return priority; }
    public void setPriority(List<TaskPriority> priority) { this.priority = priority; }

    public LocalDate getDueFrom() { return dueFrom; }
    public void setDueFrom(LocalDate dueFrom) { this.dueFrom = dueFrom; }

    public LocalDate getDueTo() { return dueTo; }
    public void setDueTo(LocalDate dueTo) { this.dueTo = dueTo; }

    public Boolean getHasDueDate() { return hasDueDate; }
    public void setHasDueDate(Boolean hasDueDate) { this.hasDueDate = hasDueDate; }

    public Boolean getOverdue() { return overdue; }
    public void setOverdue(Boolean overdue) { this.overdue = overdue; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<String> getTagsAny() { return tagsAny; }
    public void setTagsAny(List<String> tagsAny) { this.tagsAny = tagsAny; }

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }

    public OffsetDateTime getUpdatedSince() { return updatedSince; }
    public void setUpdatedSince(OffsetDateTime updatedSince) { this.updatedSince = updatedSince; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }

    public String getSortField() { return sortField; }
    public void setSortField(String sortField) { this.sortField = sortField; }

    public String getSortDir() { return sortDir; }
    public void setSortDir(String sortDir) { this.sortDir = sortDir; }
}
