package com.example.todo.validation;

import com.example.todo.exception.InvalidDateRangeException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DateRangeValidatorTest {

    // AC10: fromDate > toDate → throws InvalidDateRangeException with both param names and values
    @Test
    void fromAfterTo_throwsWithBothParamNamesAndValues() {
        LocalDate from = LocalDate.of(2026, 9, 10);
        LocalDate to   = LocalDate.of(2026, 9, 1);

        InvalidDateRangeException ex = assertThrows(InvalidDateRangeException.class,
                () -> DateRangeValidator.requireOrdered(from, to, "dueFrom", "dueTo"));

        String msg = ex.getMessage();
        assertAll(
                () -> assertTrue(msg.contains("dueFrom"), "message should contain fromParam name"),
                () -> assertTrue(msg.contains("dueTo"),   "message should contain toParam name"),
                () -> assertTrue(msg.contains(from.toString()), "message should contain from value"),
                () -> assertTrue(msg.contains(to.toString()),   "message should contain to value")
        );
    }

    @Test
    void fromEqualsTo_doesNotThrow() {
        LocalDate date = LocalDate.of(2026, 9, 5);
        assertDoesNotThrow(() -> DateRangeValidator.requireOrdered(date, date, "dueFrom", "dueTo"));
    }

    @Test
    void fromBeforeTo_doesNotThrow() {
        assertDoesNotThrow(() -> DateRangeValidator.requireOrdered(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 10),
                "dueFrom", "dueTo"));
    }

    @Test
    void nullFrom_doesNotThrow() {
        assertDoesNotThrow(() -> DateRangeValidator.requireOrdered(
                null, LocalDate.of(2026, 9, 10), "dueFrom", "dueTo"));
    }

    @Test
    void nullTo_doesNotThrow() {
        assertDoesNotThrow(() -> DateRangeValidator.requireOrdered(
                LocalDate.of(2026, 9, 1), null, "dueFrom", "dueTo"));
    }

    @Test
    void bothNull_doesNotThrow() {
        assertDoesNotThrow(() -> DateRangeValidator.requireOrdered(null, null, "dueFrom", "dueTo"));
    }
}
