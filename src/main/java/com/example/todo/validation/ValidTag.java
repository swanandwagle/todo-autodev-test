package com.example.todo.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that a {@code List<String>} contains at most 10 tags, each 1–30 characters,
 * starting with a letter or digit and containing only lowercase letters, digits, and hyphens.
 *
 * <p><strong>Normalize before validate.</strong> Always call {@link TagNormalizer#normalize}
 * on raw input before this constraint is applied so that the validator sees canonical values.
 */
@Documented
@Constraint(validatedBy = ValidTagValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTag {

    String message() default "tag contains invalid characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
