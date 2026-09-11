package com.example.todo.validation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TagNormalizerTest {

    @Test
    void nullInput_returnsEmptyList() {
        assertEquals(List.of(), TagNormalizer.normalize(null));
    }

    @Test
    void emptyList_returnsEmptyList() {
        assertEquals(List.of(), TagNormalizer.normalize(List.of()));
    }

    @Test
    void trimsWhitespace() {
        assertEquals(List.of("finance"), TagNormalizer.normalize(List.of("  finance  ")));
    }

    @Test
    void lowercases() {
        assertEquals(List.of("finance"), TagNormalizer.normalize(List.of("FINANCE")));
    }

    @Test
    void deduplicates_preservingFirstSeenOrder() {
        assertEquals(List.of("finance", "urgent"),
                TagNormalizer.normalize(List.of("Finance", " finance ", "FINANCE", "urgent")));
    }

    @Test
    void nullElementsSkipped() {
        assertEquals(List.of("finance"), TagNormalizer.normalize(Arrays.asList("finance", null)));
    }

    @Test
    void multipleTags_normalizedAndDeduped() {
        assertEquals(List.of("a", "b", "c"),
                TagNormalizer.normalize(List.of("A", "B", "C", "a", "b")));
    }

    // AC1: ["Finance", " finance ", "FINANCE"] → ["finance"]
    @Test
    void ac1_mixedCaseAndWhitespace_collapsesToSingleEntry() {
        assertEquals(List.of("finance"),
                TagNormalizer.normalize(List.of("Finance", " finance ", "FINANCE")));
    }

    // AC2: ["work", "Work", "urgent"] → ["work", "urgent"] (first-seen casing, order preserved)
    @Test
    void ac2_firstSeenCasingPreserved_orderRetained() {
        assertEquals(List.of("work", "urgent"),
                TagNormalizer.normalize(List.of("work", "Work", "urgent")));
    }
}
