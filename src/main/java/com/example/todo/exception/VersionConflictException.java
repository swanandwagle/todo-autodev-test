package com.example.todo.exception;

import java.util.UUID;

public class VersionConflictException extends RuntimeException {

    private final long expected;
    private final long actual;

    public VersionConflictException(UUID id, long expected, long actual) {
        super("Version conflict for task " + id + ": expected " + expected + " but found " + actual);
        this.expected = expected;
        this.actual = actual;
    }

    public long getExpected() { return expected; }
    public long getActual() { return actual; }
}
