package com.example.todo.service;

import com.example.todo.dto.TaskListQuery;
import com.example.todo.exception.InvalidDateRangeException;
import com.example.todo.exception.InvalidFilterException;
import com.example.todo.exception.InvalidPaginationException;
import com.example.todo.exception.InvalidSortException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TaskListQueryValidatorTest {

    private TaskListQueryValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TaskListQueryValidator();
    }

    @Test
    void defaultQuery_valid() {
        assertDoesNotThrow(() -> validator.validate(new TaskListQuery()));
    }

    @Test
    void negativePage_throws() {
        TaskListQuery q = new TaskListQuery();
        q.setPage(-1);
        assertThrows(InvalidPaginationException.class, () -> validator.validate(q));
    }

    @Test
    void zeroSize_throws() {
        TaskListQuery q = new TaskListQuery();
        q.setSize(0);
        assertThrows(InvalidPaginationException.class, () -> validator.validate(q));
    }

    @Test
    void sizeOver100_throws() {
        TaskListQuery q = new TaskListQuery();
        q.setSize(101);
        assertThrows(InvalidPaginationException.class, () -> validator.validate(q));
    }

    @Test
    void size100_valid() {
        TaskListQuery q = new TaskListQuery();
        q.setSize(100);
        assertDoesNotThrow(() -> validator.validate(q));
    }

    @Test
    void invalidSortField_throws() {
        TaskListQuery q = new TaskListQuery();
        q.setSortField("notAField");
        assertThrows(InvalidSortException.class, () -> validator.validate(q));
    }

    @Test
    void validSortFields_doNotThrow() {
        for (String field : TaskListQueryValidator.ALLOWED_SORT_FIELDS) {
            TaskListQuery q = new TaskListQuery();
            q.setSortField(field);
            assertDoesNotThrow(() -> validator.validate(q));
        }
    }

    @Test
    void invalidSortDirection_throws() {
        TaskListQuery q = new TaskListQuery();
        q.setSortField("title");
        q.setSortDir("sideways");
        assertThrows(InvalidSortException.class, () -> validator.validate(q));
    }

    @Test
    void sortDirAsc_valid() {
        TaskListQuery q = new TaskListQuery();
        q.setSortField("title");
        q.setSortDir("asc");
        assertDoesNotThrow(() -> validator.validate(q));
    }

    @Test
    void sortDirDesc_valid() {
        TaskListQuery q = new TaskListQuery();
        q.setSortField("title");
        q.setSortDir("DESC");
        assertDoesNotThrow(() -> validator.validate(q));
    }

    @Test
    void dueFromAfterDueTo_throws() {
        TaskListQuery q = new TaskListQuery();
        q.setDueFrom(LocalDate.of(2026, 12, 31));
        q.setDueTo(LocalDate.of(2026, 1, 1));
        assertThrows(InvalidDateRangeException.class, () -> validator.validate(q));
    }

    @Test
    void dueFromEqualsdueTo_valid() {
        TaskListQuery q = new TaskListQuery();
        LocalDate date = LocalDate.of(2026, 6, 1);
        q.setDueFrom(date);
        q.setDueTo(date);
        assertDoesNotThrow(() -> validator.validate(q));
    }

    @Test
    void hasDueDateFalseWithDueFrom_throws() {
        TaskListQuery q = new TaskListQuery();
        q.setHasDueDate(false);
        q.setDueFrom(LocalDate.now());
        assertThrows(InvalidFilterException.class, () -> validator.validate(q));
    }

    @Test
    void hasDueDateFalseWithDueTo_throws() {
        TaskListQuery q = new TaskListQuery();
        q.setHasDueDate(false);
        q.setDueTo(LocalDate.now());
        assertThrows(InvalidFilterException.class, () -> validator.validate(q));
    }

    @Test
    void hasDueDateTrueWithDueDates_valid() {
        TaskListQuery q = new TaskListQuery();
        q.setHasDueDate(true);
        q.setDueFrom(LocalDate.now());
        assertDoesNotThrow(() -> validator.validate(q));
    }
}
