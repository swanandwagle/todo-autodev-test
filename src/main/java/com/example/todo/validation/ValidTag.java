package com.example.todo.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidTagValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTag {

    String message() default "tag contains invalid characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
