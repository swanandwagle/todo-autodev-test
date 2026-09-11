package com.example.todo.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ValidTagValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void teardown() {
        factory.close();
    }

    // Wrapper so we can use @ValidTag on a field
    record TagHolder(@ValidTag List<String> tags) {}

    private Set<ConstraintViolation<TagHolder>> validate(List<String> tags) {
        return validator.validate(new TagHolder(tags));
    }

    @Test
    void nullList_passes() {
        assertTrue(validate(null).isEmpty());
    }

    @Test
    void emptyList_passes() {
        assertTrue(validate(List.of()).isEmpty());
    }

    // AC6: digit-or-letter start, single char passes
    @Test
    void singleLetterTag_passes() {
        assertTrue(validate(List.of("a")).isEmpty());
    }

    @Test
    void digitStartTag_passes() {
        assertTrue(validate(List.of("1abc")).isEmpty());
    }

    @Test
    void validTagWithHyphen_passes() {
        assertTrue(validate(List.of("back-end")).isEmpty());
    }

    // AC4: tag with invalid character fails and message names the tag
    @Test
    void tagWithExclamationMark_fails_messageNamesTag() {
        var violations = validate(List.of("finance!"));
        assertFalse(violations.isEmpty());
        String messages = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining());
        assertTrue(messages.contains("finance!"),
                "violation message should name the invalid tag 'finance!'");
    }

    // AC5: tag of 31 characters fails
    @Test
    void tagOf31Characters_fails() {
        String tag31 = "a".repeat(31);
        var violations = validate(List.of(tag31));
        assertFalse(violations.isEmpty(), "31-char tag should fail");
        String messages = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining());
        assertTrue(messages.contains(tag31),
                "violation message should name the invalid tag");
    }

    // AC6: tag of exactly 30 characters passes
    @Test
    void tagOf30Characters_passes() {
        String tag30 = "a".repeat(30);
        assertTrue(validate(List.of(tag30)).isEmpty(), "30-char tag should pass");
    }

    // AC7: list of 11 tags fails the size constraint
    @Test
    void elevenTags_fails() {
        List<String> tags = IntStream.rangeClosed(1, 11)
                .mapToObj(i -> "tag" + i)
                .collect(Collectors.toList());
        var violations = validate(tags);
        assertFalse(violations.isEmpty(), "11-tag list should fail size constraint");
    }

    // AC7: list of exactly 10 tags passes
    @Test
    void tenTags_passes() {
        List<String> tags = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> "tag" + i)
                .collect(Collectors.toList());
        assertTrue(validate(tags).isEmpty(), "10-tag list should pass");
    }

    @Test
    void uppercaseTag_fails() {
        var violations = validate(List.of("Finance"));
        assertFalse(violations.isEmpty());
    }

    @Test
    void tagWithUnderscore_fails() {
        var violations = validate(List.of("a_b"));
        assertFalse(violations.isEmpty());
    }
}
