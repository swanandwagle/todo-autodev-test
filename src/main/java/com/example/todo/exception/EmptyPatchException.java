package com.example.todo.exception;

public class EmptyPatchException extends RuntimeException {

    public EmptyPatchException() {
        super("Patch body must contain at least one updatable field.");
    }
}
