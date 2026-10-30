package edu.xjtlu.civa.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Checks serialized DTOs against the candidate OpenAPI schemas. */
final class DtoChecks {
    private static final String DATE = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$";
    private static final String TIME = "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z$";
    private DtoChecks() { }

    static boolean permitted(Class<?> type) {
        String name = type.getName();
        return name.startsWith("edu.xjtlu.civa.api.model.") && !name.endsWith("Request")
                && !name.endsWith("AuthSession") && !name.endsWith("Invitation") && !type.isEnum();
    }

    static boolean valid(Object value, JsonObject schemas) {
        if (value == null || schemas == null || !permitted(value.getClass())) return false;
        try {
            JsonElement schema = schemas.get(value.getClass().getSimpleName());
            return schema != null && check(ApiCalls.GSON.toJsonTree(value), schema.getAsJsonObject(), schemas, 0);
        } catch (RuntimeException ex) { return false; }
    }

    static boolean validTree(JsonElement value, JsonObject schema, JsonObject schemas) {
        try { return check(value, schema, schemas, 0); }
        catch (RuntimeException ex) { return false; }
    }

    private static boolean check(JsonElement value, JsonObject schema, JsonObject schemas, int depth) {
        if (depth > 64) return false;
        if (value == null || value.isJsonNull()) return schema.has("nullable") && schema.get("nullable").getAsBoolean();
        if (schema.has("$ref")) {
            String ref = schema.get("$ref").getAsString();
            String prefix = "#/components/schemas/";
            if (!ref.startsWith(prefix) || !schemas.has(ref.substring(prefix.length()))) return false;
            return check(value, schemas.getAsJsonObject(ref.substring(prefix.length())), schemas, depth + 1);
        }
        if (schema.has("enum")) {
            boolean found = false;
            for (JsonElement allowed : schema.getAsJsonArray("enum")) if (allowed.equals(value)) found = true;
            if (!found) return false;
        }
        if (!schema.has("type")) return false;
        return switch (schema.get("type").getAsString()) {
            case "object" -> object(value, schema, schemas, depth);
            case "array" -> array(value, schema, schemas, depth);
            case "string" -> string(value, schema);
            case "integer" -> integer(value, schema);
            case "boolean" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
            default -> false;
        };
    }

    private static boolean object(JsonElement value, JsonObject schema, JsonObject schemas, int depth) {
        if (!value.isJsonObject() || !schema.has("properties")) return false;
        JsonObject object = value.getAsJsonObject();
        JsonObject properties = schema.getAsJsonObject("properties");
        if (schema.has("required")) for (JsonElement name : schema.getAsJsonArray("required")) if (!object.has(name.getAsString())) return false;
        for (var field : properties.entrySet()) {
            if (object.has(field.getKey()) && !check(object.get(field.getKey()), field.getValue().getAsJsonObject(), schemas, depth + 1)) return false;
        }
        return true;
    }

    private static boolean array(JsonElement value, JsonObject schema, JsonObject schemas, int depth) {
        if (!value.isJsonArray() || !schema.has("items")) return false;
        JsonArray array = value.getAsJsonArray();
        if (schema.has("minItems") && array.size() < schema.get("minItems").getAsInt()) return false;
        if (schema.has("maxItems") && array.size() > schema.get("maxItems").getAsInt()) return false;
        Set<JsonElement> unique = schema.has("uniqueItems") && schema.get("uniqueItems").getAsBoolean() ? new HashSet<>() : null;
        for (JsonElement item : array) {
            if (!check(item, schema.getAsJsonObject("items"), schemas, depth + 1) || (unique != null && !unique.add(item))) return false;
        }
        return true;
    }

    private static boolean string(JsonElement value, JsonObject schema) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return false;
        String text = value.getAsString();
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(text)) return false;
        int length = text.codePointCount(0, text.length());
        if (schema.has("minLength") && length < schema.get("minLength").getAsInt()) return false;
        if (schema.has("maxLength") && length > schema.get("maxLength").getAsInt()) return false;
        if (schema.has("format") && schema.get("format").getAsString().equals("uuid")
                && !UUID.fromString(text).toString().equalsIgnoreCase(text)) return false;
        if (schema.has("pattern")) {
            String pattern = schema.get("pattern").getAsString();
            if (!Pattern.compile(pattern).matcher(text).matches()) return false;
            if (pattern.equals(DATE)) LocalDate.parse(text);
            if (pattern.equals(TIME)) LocalDateTime.parse(text.substring(0, text.length() - 1));
        }
        return true;
    }

    private static boolean integer(JsonElement value, JsonObject schema) {
        if (!value.isJsonPrimitive()) return false;
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isNumber()) return false;
        BigDecimal decimal = primitive.getAsBigDecimal();
        BigInteger integer = decimal.toBigIntegerExact();
        if (schema.has("minimum") && decimal.compareTo(schema.get("minimum").getAsBigDecimal()) < 0) return false;
        if (schema.has("maximum") && decimal.compareTo(schema.get("maximum").getAsBigDecimal()) > 0) return false;
        if (schema.has("format")) {
            String format = schema.get("format").getAsString();
            if (format.equals("int32") && integer.bitLength() > 31) return false;
            if (format.equals("int64") && integer.bitLength() > 63) return false;
        }
        return true;
    }
}
