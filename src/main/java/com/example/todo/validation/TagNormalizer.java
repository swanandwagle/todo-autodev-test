package com.example.todo.validation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Normalizes a list of tag strings: trims whitespace, lowercases, and deduplicates
 * while preserving the first-seen order.
 *
 * <p><strong>Normalize before validate.</strong> Always call {@link #normalize} on raw input
 * before applying {@link ValidTag} so that the constraint sees canonical values.
 */
public class TagNormalizer {

    private TagNormalizer() {}

    public static List<String> normalize(List<String> tags) {
        if (tags == null) return new ArrayList<>();
        var seen = new LinkedHashSet<String>();
        for (String tag : tags) {
            if (tag != null) {
                seen.add(tag.trim().toLowerCase());
            }
        }
        return new ArrayList<>(seen);
    }
}
