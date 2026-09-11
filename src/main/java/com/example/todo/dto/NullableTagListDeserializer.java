package com.example.todo.dto;

import com.example.todo.validation.TagNormalizer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.openapitools.jackson.nullable.JsonNullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NullableTagListDeserializer extends JsonDeserializer<JsonNullable<List<String>>> {

    @Override
    public JsonNullable<List<String>> deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node.isNull()) {
            return JsonNullable.of(null);
        }
        if (!node.isArray()) {
            return JsonNullable.of(new ArrayList<>());
        }
        List<String> raw = new ArrayList<>();
        for (JsonNode element : node) {
            raw.add(element.isNull() ? null : element.asText());
        }
        return JsonNullable.of(TagNormalizer.normalize(raw));
    }
}
