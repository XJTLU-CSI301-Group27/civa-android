package edu.xjtlu.civa.server.application;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.lang.Nullable;

/** Canonical JSON digest for candidate idempotent requests; business validation remains required. */
public final class CanonicalRequestDigest {
    private static final String QUANTITY_PATTERN = "^(0|[1-9][0-9]{0,14})(\\.[0-9]{1,3})?$";
    private static final String DATE_PATTERN = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$";
    private static final String TIME_PATTERN = "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z$";
    private static final Pattern PATH = Pattern.compile("^/api/v1/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*$");
    private static final Pattern UUID_SEGMENT = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private final ObjectMapper mapper;
    private final JsonNode schemas;

    public CanonicalRequestDigest(ObjectMapper mapper, JsonNode schemas) {
        this.mapper = Objects.requireNonNull(mapper);
        this.schemas = Objects.requireNonNull(schemas);
    }

    public byte[] digestJson(String method, String path, @Nullable String payload, @Nullable JsonNode requestSchema) {
        if (method == null || !method.matches("[A-Za-z]+") || path == null || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid method or path");
        }
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) if (UUID_SEGMENT.matcher(parts[i]).matches()) parts[i] = parts[i].toLowerCase(Locale.ROOT);
        String normalizedPath = String.join("/", parts);
        try {
            byte[] canonical = null;
            if (requestSchema == null) {
                if (payload != null) throw new IllegalArgumentException("Unexpected request body");
            } else {
                if (payload == null) throw new IllegalArgumentException("Missing request body");
                JsonNode input = mapper.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(payload);
                if (input == null) throw new IllegalArgumentException("Missing request body");
                canonical = mapper.writer().without(SerializationFeature.INDENT_OUTPUT)
                        .without(JsonGenerator.Feature.ESCAPE_NON_ASCII).writeValueAsBytes(normalize(input, requestSchema));
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                writePart(out, method.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
                writePart(out, normalizedPath.getBytes(StandardCharsets.UTF_8));
                writePart(out, canonical);
            }
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid JSON request");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static void writePart(DataOutputStream out, byte[] part) throws IOException {
        out.writeInt(part == null ? -1 : part.length);
        if (part != null) out.write(part);
    }

    private JsonNode normalize(JsonNode value, JsonNode schema) {
        boolean nullable = schema.path("nullable").asBoolean(false);
        if (value.isNull()) {
            if (!nullable) throw new IllegalArgumentException("Unexpected null");
            return NullNode.instance;
        }
        if (schema.has("$ref")) {
            String ref = schema.path("$ref").asText();
            String prefix = "#/components/schemas/";
            if (!ref.startsWith(prefix) || !schemas.has(ref.substring(prefix.length()))) throw new IllegalArgumentException("Unresolved schema");
            return normalize(value, schemas.get(ref.substring(prefix.length())));
        }
        if (schema.has("allOf")) {
            JsonNode allOf = schema.get("allOf");
            if (!allOf.isArray() || allOf.size() != 1) throw new IllegalArgumentException("Unsupported composed schema");
            return normalize(value, allOf.get(0));
        }
        if (schema.has("enum")) {
            boolean found = false;
            for (JsonNode allowed : schema.get("enum")) if (allowed.equals(value)) found = true;
            if (!found) throw new IllegalArgumentException("Invalid enum value");
        }
        switch (schema.path("type").asText()) {
            case "object":
                if (!value.isObject()) throw new IllegalArgumentException("Expected object");
                JsonNode properties = schema.path("properties");
                Iterator<String> provided = value.fieldNames();
                while (provided.hasNext()) if (!properties.has(provided.next())) throw new IllegalArgumentException("Unknown field");
                List<String> names = new ArrayList<>();
                properties.fieldNames().forEachRemaining(names::add);
                names.sort(String::compareTo);
                ObjectNode object = mapper.createObjectNode();
                for (String name : names) {
                    JsonNode fieldSchema = properties.get(name);
                    if (value.has(name)) object.set(name, normalize(value.get(name), fieldSchema));
                    else if (isRequired(schema, name)) throw new IllegalArgumentException("Missing required field");
                    else if (fieldSchema.path("nullable").asBoolean(false)) object.set(name, NullNode.instance);
                }
                return object;
            case "array":
                if (!value.isArray()) throw new IllegalArgumentException("Expected array");
                if (schema.has("minItems") && value.size() < schema.get("minItems").asInt()) throw new IllegalArgumentException("Array too small");
                if (schema.has("maxItems") && value.size() > schema.get("maxItems").asInt()) throw new IllegalArgumentException("Array too large");
                ArrayNode array = mapper.createArrayNode();
                for (JsonNode element : value) array.add(normalize(element, schema.path("items")));
                return array;
            case "string":
                if (!value.isTextual()) throw new IllegalArgumentException("Expected string");
                String text = value.textValue();
                if (!StandardCharsets.UTF_8.newEncoder().canEncode(text)) throw new IllegalArgumentException("Invalid Unicode");
                int length = text.codePointCount(0, text.length());
                if (schema.has("minLength") && length < schema.get("minLength").asInt()) throw new IllegalArgumentException("String too short");
                if (schema.has("maxLength") && length > schema.get("maxLength").asInt()) throw new IllegalArgumentException("String too long");
                String pattern = schema.path("pattern").asText();
                if (!pattern.isEmpty() && !Pattern.compile(pattern).matcher(text).matches()) throw new IllegalArgumentException("Invalid string format");
                try {
                    if (DATE_PATTERN.equals(pattern)) LocalDate.parse(text);
                    if (TIME_PATTERN.equals(pattern)) LocalDateTime.parse(text.substring(0, text.length() - 1));
                } catch (DateTimeParseException ex) {
                    throw new IllegalArgumentException("Invalid date or time");
                }
                if (QUANTITY_PATTERN.equals(pattern)) return TextNode.valueOf(new BigDecimal(text).stripTrailingZeros().toPlainString());
                return value;
            case "integer":
                if (!value.isIntegralNumber()) throw new IllegalArgumentException("Expected integer");
                return value;
            case "boolean":
                if (!value.isBoolean()) throw new IllegalArgumentException("Expected boolean");
                return value;
            default: throw new IllegalArgumentException("Unsupported schema type");
        }
    }

    private static boolean isRequired(JsonNode schema, String name) {
        for (JsonNode required : schema.path("required")) if (name.equals(required.asText())) return true;
        return false;
    }
}
