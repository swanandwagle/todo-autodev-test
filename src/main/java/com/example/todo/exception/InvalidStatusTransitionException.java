package com.example.todo.exception;

import com.example.todo.domain.TaskStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    private final TaskStatus from;
    private final TaskStatus to;

    public InvalidStatusTransitionException(TaskStatus from, TaskStatus to) {
        super("Invalid status transition from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public TaskStatus getFrom() { return from; }
    public TaskStatus getTo() { return to; }
}
