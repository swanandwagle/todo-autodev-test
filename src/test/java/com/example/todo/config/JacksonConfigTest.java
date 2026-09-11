package com.example.todo.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JacksonConfig — no Spring context required.
 * Covers AC8 (unknown fields fail) and AC9 (case-sensitive enum deserialization).
 */
class JacksonConfigTest {

    private ObjectMapper mapper;

    enum Status { TODO, IN_PROGRESS, DONE }

    record WithStatus(Status status) {}

    record WithTitle(String title) {}

    @BeforeEach
    void setup() {
        mapper = new JacksonConfig().objectMapper();
    }

    // Verify the bean is configured the way the config declares
    @Test
    void objectMapper_hasFailOnUnknownPropertiesEnabled() {
        assertTrue(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    // AC8: unknown field in JSON body → deserialization fails
    @Test
    void unknownField_throwsUnrecognizedPropertyException() {
        String json = "{\"title\":\"Test\",\"unknownField\":\"value\"}";
        assertThrows(UnrecognizedPropertyException.class,
                () -> mapper.readValue(json, WithTitle.class),
                "unknown JSON field should cause UnrecognizedPropertyException");
    }

    // AC9: lowercase enum value when expecting uppercase → deserialization fails
    @Test
    void lowercaseEnumValue_throwsInvalidFormatException() {
        String json = "{\"status\":\"todo\"}";
        assertThrows(InvalidFormatException.class,
                () -> mapper.readValue(json, WithStatus.class),
                "lowercase 'todo' should fail for case-sensitive enum TODO");
    }

    // Sanity: correct enum value works
    @Test
    void uppercaseEnumValue_deserializesSuccessfully() throws Exception {
        String json = "{\"status\":\"TODO\"}";
        WithStatus result = mapper.readValue(json, WithStatus.class);
        assertEquals(Status.TODO, result.status());
    }

    // Sanity: known field deserializes fine
    @Test
    void knownField_deserializesSuccessfully() throws Exception {
        String json = "{\"title\":\"Buy milk\"}";
        WithTitle result = mapper.readValue(json, WithTitle.class);
        assertEquals("Buy milk", result.title());
    }
}
