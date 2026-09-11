package com.example.todo.service;

import com.example.todo.domain.TaskStatus;
import com.example.todo.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class TaskStatusTransitionValidatorTest {

    private TaskStatusTransitionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TaskStatusTransitionValidator();
    }

    @ParameterizedTest
    @CsvSource({
        "TODO, TODO",
        "TODO, IN_PROGRESS",
        "TODO, DONE",
        "TODO, CANCELLED",
        "IN_PROGRESS, IN_PROGRESS",
        "IN_PROGRESS, TODO",
        "IN_PROGRESS, DONE",
        "IN_PROGRESS, CANCELLED",
        "DONE, DONE",
        "DONE, TODO",
        "DONE, IN_PROGRESS",
        "CANCELLED, CANCELLED",
    })
    void allowedTransitions_doNotThrow(TaskStatus from, TaskStatus to) {
        assertDoesNotThrow(() -> validator.validate(from, to));
    }

    @Test
    void cancelled_toTodo_throws() {
        assertThrows(InvalidStatusTransitionException.class,
                () -> validator.validate(TaskStatus.CANCELLED, TaskStatus.TODO));
    }

    @Test
    void cancelled_toInProgress_throws() {
        assertThrows(InvalidStatusTransitionException.class,
                () -> validator.validate(TaskStatus.CANCELLED, TaskStatus.IN_PROGRESS));
    }

    @Test
    void cancelled_toDone_throws() {
        assertThrows(InvalidStatusTransitionException.class,
                () -> validator.validate(TaskStatus.CANCELLED, TaskStatus.DONE));
    }
}
