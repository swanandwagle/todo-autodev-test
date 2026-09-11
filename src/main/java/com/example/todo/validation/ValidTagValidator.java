package com.example.todo.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

/**
 * Validates a list of already-normalized tags (normalize before validate).
 * Each tag must match {@code ^[a-z0-9][a-z0-9-]{0,29}$} (1–30 chars).
 * The list must contain at most 10 tags.
 */
public class ValidTagValidator implements ConstraintValidator<ValidTag, List<String>> {

    private static final java.util.regex.Pattern VALID_TAG =
            java.util.regex.Pattern.compile("^[a-z0-9][a-z0-9-]{0,29}$");
    private static final int MAX_TAGS = 10;

    @Override
    public boolean isValid(List<String> tags, ConstraintValidatorContext context) {
        if (tags == null) return true;
        boolean allValid = true;
        context.disableDefaultConstraintViolation();
        if (tags.size() > MAX_TAGS) {
            context.buildConstraintViolationWithTemplate(
                    "tag list must contain at most " + MAX_TAGS + " tags"
            ).addConstraintViolation();
            allValid = false;
        }
        for (String tag : tags) {
            if (tag != null && !VALID_TAG.matcher(tag).matches()) {
                context.buildConstraintViolationWithTemplate(
                        "tag '" + tag + "' is invalid; tags must be 1–30 characters, start with a letter or digit, and contain only lowercase letters, digits, and hyphens"
                ).addConstraintViolation();
                allValid = false;
            }
        }
        return allValid;
    }
}
