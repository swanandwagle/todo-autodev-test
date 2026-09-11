package com.example.todo.validation;

import com.example.todo.domain.TaskStatus;
import com.example.todo.dto.PatchTaskRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ValidPatchValidatorTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // --- title: null when present → violation ---

    @Test
    void titleSetToNull_producesViolation() {
        PatchTaskRequest req = new PatchTaskRequest();
        req.setTitle(JsonNullable.of(null));

        Set<ConstraintViolation<PatchTaskRequest>> violations = validator.validate(req);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("title")),
                "Expected violation on 'title' field");
    }

    // --- status: null when present → violation ---

    @Test
    void statusSetToNull_producesViolation() {
        PatchTaskRequest req = new PatchTaskRequest();
        req.setStatus(JsonNullable.of(null));

        Set<ConstraintViolation<PatchTaskRequest>> violations = validator.validate(req);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("status")),
                "Expected violation on 'status' field");
    }

    // --- priority: null when present → violation ---

    @Test
    void prioritySetToNull_producesViolation() {
        PatchTaskRequest req = new PatchTaskRequest();
        req.setPriority(JsonNullable.of(null));

        Set<ConstraintViolation<PatchTaskRequest>> violations = validator.validate(req);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("priority")),
                "Expected violation on 'priority' field");
    }

    // --- all undefined (empty patch) → no violations from ValidPatch (service handles EMPTY_PATCH) ---

    @Test
    void allFieldsUndefined_noValidationViolations() {
        PatchTaskRequest req = new PatchTaskRequest();

        Set<ConstraintViolation<PatchTaskRequest>> violations = validator.validate(req);

        assertTrue(violations.isEmpty(), "Expected no BV violations for empty patch (EMPTY_PATCH handled by service)");
    }

    // --- title with valid value → no violation ---

    @Test
    void titlePresent_validValue_noViolation() {
        PatchTaskRequest req = new PatchTaskRequest();
        req.setTitle(JsonNullable.of("Some title"));

        Set<ConstraintViolation<PatchTaskRequest>> violations = validator.validate(req);

        assertTrue(violations.isEmpty());
    }

    // --- status with valid value → no violation ---

    @Test
    void statusPresent_validValue_noViolation() {
        PatchTaskRequest req = new PatchTaskRequest();
        req.setStatus(JsonNullable.of(TaskStatus.IN_PROGRESS));

        Set<ConstraintViolation<PatchTaskRequest>> violations = validator.validate(req);

        assertTrue(violations.isEmpty());
    }

    // --- description: null is allowed (nullable field) → no ValidPatch violation ---

    @Test
    void descriptionSetToNull_noViolation() {
        PatchTaskRequest req = new PatchTaskRequest();
        req.setDescription(JsonNullable.of(null));

        Set<ConstraintViolation<PatchTaskRequest>> violations = validator.validate(req);

        assertTrue(violations.isEmpty(), "description null is valid (nullable field)");
    }

    // --- violation message contains expected text ---

    @Test
    void titleSetToNull_violationMessageIndicatesCannotBeNull() {
        PatchTaskRequest req = new PatchTaskRequest();
        req.setTitle(JsonNullable.of(null));

        Set<ConstraintViolation<PatchTaskRequest>> violations = validator.validate(req);
        ConstraintViolation<PatchTaskRequest> violation = violations.stream()
                .filter(v -> v.getPropertyPath().toString().contains("title"))
                .findFirst()
                .orElseThrow();

        assertTrue(violation.getMessage().contains("cannot be null"),
                "Message should state cannot be null, got: " + violation.getMessage());
    }
}
