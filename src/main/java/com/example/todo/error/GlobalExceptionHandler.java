package com.example.todo.error;

import com.example.todo.exception.TaskNotFoundException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        ProblemDetail pd = ProblemDetailFactory.create(
                400, "VALIDATION_FAILED", "Validation failed",
                "One or more request fields are invalid.", request);

        List<Map<String, Object>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.<String, Object>of(
                        "field", fe.getField(),
                        "rejectedValue", fe.getRejectedValue() != null ? fe.getRejectedValue() : "",
                        "message", fe.getDefaultMessage()))
                .toList();
        pd.setProperty("errors", errors);
        return problemResponse(pd);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable(HttpMessageNotReadableException ex,
                                                            HttpServletRequest request) {
        Throwable cause = ex.getCause();

        if (cause instanceof UnrecognizedPropertyException upe) {
            ProblemDetail pd = ProblemDetailFactory.create(400, "VALIDATION_FAILED",
                    "Validation failed", "One or more request fields are invalid.", request);
            List<Map<String, Object>> errors = List.of(Map.of(
                    "field", upe.getPropertyName(),
                    "rejectedValue", upe.getPropertyName(),
                    "message", "Unknown field '" + upe.getPropertyName() + "'"));
            pd.setProperty("errors", errors);
            return problemResponse(pd);
        }

        String detail = "The request body could not be parsed.";
        if (cause instanceof InvalidFormatException ife) {
            detail = "Invalid value '" + ife.getValue() + "' for field '" +
                     fieldPath(ife) + "'.";
        }
        ProblemDetail pd = ProblemDetailFactory.create(400, "MALFORMED_REQUEST",
                "Malformed request", detail, request);
        return problemResponse(pd);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMedia(HttpMediaTypeNotSupportedException ex,
                                                                  HttpServletRequest request) {
        ProblemDetail pd = ProblemDetailFactory.create(415, "UNSUPPORTED_MEDIA_TYPE",
                "Unsupported media type",
                "Content-Type '" + ex.getContentType() + "' is not supported.", request);
        return problemResponse(pd);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                             HttpServletRequest request) {
        ProblemDetail pd = ProblemDetailFactory.create(400, "INVALID_ID_FORMAT",
                "Invalid ID format",
                "Path variable '" + ex.getName() + "' is not a valid UUID.", request);
        return problemResponse(pd);
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTaskNotFound(TaskNotFoundException ex,
                                                             HttpServletRequest request) {
        ProblemDetail pd = ProblemDetailFactory.create(404, "TASK_NOT_FOUND", "Task not found",
                ex.getMessage(), request);
        return problemResponse(pd);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex,
                                                           HttpServletRequest request) {
        ProblemDetail pd = ProblemDetailFactory.create(404, "NOT_FOUND", "Not found",
                ex.getMessage(), request);
        return problemResponse(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail pd = ProblemDetailFactory.create(500, "INTERNAL_ERROR",
                "Internal server error", "An unexpected error occurred.", request);
        return problemResponse(pd);
    }

    private ResponseEntity<ProblemDetail> problemResponse(ProblemDetail pd) {
        return ResponseEntity.status(pd.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    private String fieldPath(InvalidFormatException ife) {
        var path = ife.getPath();
        if (path == null || path.isEmpty()) return "unknown";
        return path.stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .reduce((a, b) -> a + "." + b)
                .orElse("unknown");
    }
}
