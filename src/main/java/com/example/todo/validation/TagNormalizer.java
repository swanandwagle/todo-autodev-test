package com.example.todo.validation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

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
