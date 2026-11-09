package edu.xjtlu.civa.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;
import java.io.EOFException;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.util.HashSet;
import java.util.Set;

/** Per-call validation state, enabled only for cached GET responses. */
final class RawResponseValidation {
    private Class<?> type;
    private JsonObject schemas;

    synchronized void configure(Class<?> type, JsonObject schemas) {
        if (this.type != null) throw new IllegalStateException("Call already configured for cache");
        if (schemas == null || !schemas.has(type.getSimpleName())) throw new IllegalArgumentException("Response schema unavailable");
        this.type = type;
        this.schemas = schemas;
    }

    synchronized boolean enabled() { return type != null; }

    void validate(Reader source) throws IOException {
        Class<?> expected;
        JsonObject definitions;
        synchronized (this) { expected = type; definitions = schemas; }
        if (expected == null) return;
        // An empty body means the transport delivered nothing, not a malformed payload.
        PushbackReader pushback = new PushbackReader(source, 1);
        int first = pushback.read();
        if (first == -1) throw new IOException("Empty response body");
        pushback.unread(first);
        try {
            JsonReader reader = new JsonReader(pushback);
            reader.setStrictness(Strictness.STRICT);
            JsonElement value = read(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT
                    || !DtoChecks.validTree(value, definitions.getAsJsonObject(expected.getSimpleName()), definitions)) {
                throw new InvalidResponseException();
            }
        } catch (MalformedJsonException | EOFException | CharacterCodingException | IllegalStateException | NumberFormatException ex) {
            throw new InvalidResponseException();
        }
    }

    // ponytail: buffers one response tree; use streaming schema validation if response sizes become large.
    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 64) throw new InvalidResponseException();
        switch (reader.peek()) {
            case BEGIN_OBJECT: {
                JsonObject object = new JsonObject();
                Set<String> names = new HashSet<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) throw new InvalidResponseException();
                    object.add(name, read(reader, depth + 1));
                }
                reader.endObject();
                return object;
            }
            case BEGIN_ARRAY: {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(read(reader, depth + 1));
                reader.endArray();
                return array;
            }
            case STRING: return new JsonPrimitive(reader.nextString());
            case NUMBER: return new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL: reader.nextNull(); return JsonNull.INSTANCE;
            default: throw new InvalidResponseException();
        }
    }

    static final class InvalidResponseException extends IOException { }
}
