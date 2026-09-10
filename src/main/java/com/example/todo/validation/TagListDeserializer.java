package com.example.todo.validation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TagListDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (!node.isArray()) return new ArrayList<>();
        List<String> raw = new ArrayList<>();
        for (JsonNode element : node) {
            raw.add(element.isNull() ? null : element.asText());
        }
        return TagNormalizer.normalize(raw);
    }
}
