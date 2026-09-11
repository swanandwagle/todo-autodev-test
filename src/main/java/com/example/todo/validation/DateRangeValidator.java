package com.example.todo.validation;

import com.example.todo.exception.InvalidDateRangeException;

import java.time.LocalDate;

/**
 * Utility for validating date range parameters.
 * Call {@link #requireOrdered} before applying any other date-based logic.
 */
public class DateRangeValidator {

    private DateRangeValidator() {}

    /**
     * Throws {@link InvalidDateRangeException} when {@code from} is strictly after {@code to}.
     * Null values are allowed (treated as unbounded).
     *
     * @param from      the start date (inclusive), or null
     * @param to        the end date (inclusive), or null
     * @param fromParam request parameter name for the start date, used in the error message
     * @param toParam   request parameter name for the end date, used in the error message
     */
    public static void requireOrdered(LocalDate from, LocalDate to, String fromParam, String toParam) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException(
                    "'" + fromParam + "' (" + from + ") must not be after '" + toParam + "' (" + to + ")"
            );
        }
    }
}
