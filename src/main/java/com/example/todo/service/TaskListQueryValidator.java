package com.example.todo.service;

import com.example.todo.dto.TaskListQuery;
import com.example.todo.exception.InvalidDateRangeException;
import com.example.todo.exception.InvalidFilterException;
import com.example.todo.exception.InvalidPaginationException;
import com.example.todo.exception.InvalidSortException;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TaskListQueryValidator {

    private static final int MAX_PAGE_SIZE = 100;

    static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "dueDate", "createdAt", "updatedAt", "priority", "title", "status"
    );

    public void validate(TaskListQuery q) {
        validatePagination(q);
        validateSort(q);
        validateDateRange(q);
        validateFilterCombinations(q);
    }

    private void validatePagination(TaskListQuery q) {
        int page = q.getPage() != null ? q.getPage() : 0;
        int size = q.getSize() != null ? q.getSize() : 20;
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPaginationException(
                    "page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private void validateSort(TaskListQuery q) {
        if (q.getSortField() == null) return;
        if (!ALLOWED_SORT_FIELDS.contains(q.getSortField())) {
            throw new InvalidSortException(
                    "'" + q.getSortField() + "' is not a valid sort field. " +
                    "Allowed: " + ALLOWED_SORT_FIELDS);
        }
        String dir = q.getSortDir();
        if (dir != null && !dir.equalsIgnoreCase("asc") && !dir.equalsIgnoreCase("desc")) {
            throw new InvalidSortException(
                    "Sort direction must be 'asc' or 'desc', got: '" + dir + "'");
        }
    }

    private void validateDateRange(TaskListQuery q) {
        if (q.getDueFrom() != null && q.getDueTo() != null
                && q.getDueFrom().isAfter(q.getDueTo())) {
            throw new InvalidDateRangeException(
                    "dueFrom (" + q.getDueFrom() + ") must not be after dueTo (" + q.getDueTo() + ")");
        }
    }

    private void validateFilterCombinations(TaskListQuery q) {
        // hasDueDate=false combined with dueFrom or dueTo is contradictory
        if (Boolean.FALSE.equals(q.getHasDueDate())
                && (q.getDueFrom() != null || q.getDueTo() != null)) {
            throw new InvalidFilterException(
                    "hasDueDate=false cannot be combined with dueFrom or dueTo");
        }
    }
}
