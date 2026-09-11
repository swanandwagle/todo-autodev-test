package com.example.todo.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidPatchValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPatch {

    String message() default "Patch body must contain at least one updatable field.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
