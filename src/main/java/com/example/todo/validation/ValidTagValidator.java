package com.example.todo.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

public class ValidTagValidator implements ConstraintValidator<ValidTag, List<String>> {

    private static final java.util.regex.Pattern VALID_TAG = java.util.regex.Pattern.compile("^[a-z0-9_-]+$");

    @Override
    public boolean isValid(List<String> tags, ConstraintValidatorContext context) {
        if (tags == null) return true;
        boolean allValid = true;
        context.disableDefaultConstraintViolation();
        for (String tag : tags) {
            if (tag != null && !VALID_TAG.matcher(tag).matches()) {
                context.buildConstraintViolationWithTemplate(
                        "tag '" + tag + "' contains invalid characters; only lowercase letters, digits, hyphens, and underscores are allowed"
                ).addConstraintViolation();
                allValid = false;
            }
        }
        return allValid;
    }
}
