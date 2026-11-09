package edu.xjtlu.civa.server.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Shared JSONB conversion using the application's Jackson configuration. */
@Component
public final class JsonbCodec {
    private final ObjectMapper json;

    public JsonbCodec(ObjectMapper json) { this.json = Objects.requireNonNull(json); }

    public ObjectNode object() { return json.createObjectNode(); }

    public String write(JsonNode value) {
        Objects.requireNonNull(value);
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Invalid JSON value"); }
    }

    public JsonNode read(String value) {
        Objects.requireNonNull(value);
        try {
            JsonNode result = json.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(value);
            if (result == null || result.isMissingNode()) throw new IllegalArgumentException("Missing JSON value");
            return result;
        } catch (JsonProcessingException ex) { throw new IllegalArgumentException("Invalid JSON value"); }
    }
}
