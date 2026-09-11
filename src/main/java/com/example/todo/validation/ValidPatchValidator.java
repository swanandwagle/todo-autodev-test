package com.example.todo.validation;

import com.example.todo.dto.PatchTaskRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.openapitools.jackson.nullable.JsonNullable;

public class ValidPatchValidator implements ConstraintValidator<ValidPatch, PatchTaskRequest> {

    @Override
    public boolean isValid(PatchTaskRequest req, ConstraintValidatorContext ctx) {
        if (req == null) return true;

        boolean valid = true;
        ctx.disableDefaultConstraintViolation();

        if (isPresentAndNull(req.getTitle())) {
            ctx.buildConstraintViolationWithTemplate(
                    "cannot be null; omit to keep current value")
                    .addPropertyNode("title")
                    .addConstraintViolation();
            valid = false;
        }

        if (isPresentAndNull(req.getStatus())) {
            ctx.buildConstraintViolationWithTemplate(
                    "cannot be null; omit to keep current value")
                    .addPropertyNode("status")
                    .addConstraintViolation();
            valid = false;
        }

        if (isPresentAndNull(req.getPriority())) {
            ctx.buildConstraintViolationWithTemplate(
                    "cannot be null; omit to keep current value")
                    .addPropertyNode("priority")
                    .addConstraintViolation();
            valid = false;
        }

        return valid;
    }

    private boolean isPresentAndNull(JsonNullable<?> field) {
        return field != null && field.isPresent() && field.get() == null;
    }
}
