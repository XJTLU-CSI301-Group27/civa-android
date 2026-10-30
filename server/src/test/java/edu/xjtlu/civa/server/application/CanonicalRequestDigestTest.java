package edu.xjtlu.civa.server.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.JsonGenerator;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalRequestDigestTest {
    @Test void canonicalizesEquivalentRequestsWithoutReorderingArrays() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode schemas = mapper.readTree(Path.of(System.getProperty("civa.contractDraft")).toFile()).path("components").path("schemas");
        var digest = new CanonicalRequestDigest(mapper, schemas);
        JsonNode inventory = schemas.get("CreateInventoryBatchRequest");
        String id = "7e0dd3fb-30d6-4df5-b495-fd067610db79";
        String path = "/api/v1/households/" + id + "/inventory-batches";
        String first = "{\"ingredientId\":\"" + id + "\",\"quantity\":\"2.500\",\"unit\":\"G\",\"location\":\"\"}";
        String second = "{\"expiresOn\":null,\"location\":\"\",\"unit\":\"G\",\"quantity\":\"2.5\",\"ingredientId\":\"" + id + "\"}";
        byte[] expected = digest.digestJson("POST", path, first, inventory);
        assertEquals("9f91425879a52fd132c40c4eff5354a386adca910e6961dd2279331b3930ca51", HexFormat.of().formatHex(expected));
        assertArrayEquals(expected, digest.digestJson("post", path.replace(id, id.toUpperCase(Locale.ROOT)), second, inventory));
        var configuredMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).enable(JsonGenerator.Feature.ESCAPE_NON_ASCII);
        var configured = new CanonicalRequestDigest(configuredMapper, schemas);
        String unicode = first.replace("\"location\":\"\"", "\"location\":\"厨房\"");
        assertArrayEquals(digest.digestJson("POST", path, unicode, inventory), configured.digestJson("POST", path, unicode, inventory));
        assertFalse(Arrays.equals(expected, digest.digestJson("POST", "/api/v1/households/" + id + "/recipes", second, inventory)));
        assertThrows(IllegalArgumentException.class, () -> digest.digestJson("POST", path, first.replace("2.500", "2.5000"), inventory));
        assertThrows(IllegalArgumentException.class, () -> digest.digestJson("POST", path, first.replace("\"unit\":\"G\"", "\"unit\":\"OZ\""), inventory));
        assertThrows(IllegalArgumentException.class, () -> digest.digestJson("POST", path, first.replace("\"unit\":\"G\"", "\"unit\":\"G\",\"unit\":\"G\""), inventory));
        assertThrows(IllegalArgumentException.class, () -> digest.digestJson("POST", path, first.replace("\"location\":\"\"", "\"location\":\"\",\"unknown\":1"), inventory));
        assertThrows(IllegalArgumentException.class, () -> digest.digestJson("POST", path, first + "{}", inventory));
        assertThrows(IllegalArgumentException.class, () -> digest.digestJson("POST", path, first.replace("\"location\":\"\"", "\"location\":\"\\uD800\""), inventory));
        assertThrows(IllegalArgumentException.class, () -> digest.digestJson("POST", path + "?x=1", first, inventory));
        assertThrows(IllegalArgumentException.class, () -> digest.digestJson("POST", path, "null", null));
        assertEquals(32, digest.digestJson("POST", path, null, null).length);

        JsonNode plan = schemas.get("PlanCommitRequest");
        String slotA = "{\"date\":\"2026-01-01\",\"mealType\":\"LUNCH\",\"expectedVersion\":0,\"items\":[]}";
        String slotB = "{\"date\":\"2026-01-02\",\"mealType\":\"DINNER\",\"expectedVersion\":0,\"items\":[]}";
        String planPath = "/api/v1/households/" + id + "/plan-commits";
        assertFalse(Arrays.equals(digest.digestJson("POST", planPath, "{\"slots\":[" + slotA + "," + slotB + "]}", plan),
                digest.digestJson("POST", planPath, "{\"slots\":[" + slotB + "," + slotA + "]}", plan)));
        assertDoesNotThrow(() -> digest.digestJson("POST", planPath, "{\"slots\":[" + slotA.replace("2026-01-01", "2024-02-29") + "]}", plan));
        assertThrows(IllegalArgumentException.class, () -> digest.digestJson("POST", planPath, "{\"slots\":[" + slotA.replace("2026-01-01", "2026-02-29") + "]}", plan));

        JsonNode timestamp = schemas.get("ConfirmCookingRequest").path("properties").path("cookedAt");
        assertDoesNotThrow(() -> digest.digestJson("POST", path, "\"2024-02-29T23:59:59.123Z\"", timestamp));
        assertThrows(IllegalArgumentException.class, () -> digest.digestJson("POST", path, "\"2024-02-30T23:59:59.123Z\"", timestamp));
        assertThrows(IllegalArgumentException.class, () -> digest.digestJson("POST", path, "\"2024-02-29T24:00:00.000Z\"", timestamp));
        assertThrows(IllegalArgumentException.class, () -> digest.digestJson("POST", path, "\"2024-02-29T23:59:60.000Z\"", timestamp));
    }
}
