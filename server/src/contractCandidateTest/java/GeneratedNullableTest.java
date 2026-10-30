import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.xjtlu.civa.server.api.model.CreateRecipeRequest;
import edu.xjtlu.civa.server.api.model.User;
import edu.xjtlu.civa.server.api.model.RegisterRequest;
import edu.xjtlu.civa.server.api.model.ApiError;
import jakarta.validation.Validation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedNullableTest {
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test void everyDecodedSchemaPatternCompiles() throws Exception {
        var schemas = mapper.readTree(Path.of(System.getProperty("civa.contractDraft")).toFile()).path("components").path("schemas");
        assertTrue(checkPatterns(schemas) > 0);
    }

    private int checkPatterns(com.fasterxml.jackson.databind.JsonNode node) {
        int count = 0;
        if (node.isObject() && node.has("pattern")) {
            Pattern.compile(node.get("pattern").asText());
            count++;
        }
        if (node.isContainerNode()) for (var child : node) count += checkPatterns(child);
        return count;
    }

    @Test void requiredNullableResponseCanContainNull() {
        User user = new User(UUID.randomUUID(), "2026-01-01T00:00:00.000Z", "2026-01-01T00:00:00.000Z", "alice", "Alice", "Asia/Shanghai", null);
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertTrue(validator.validate(user).isEmpty());
        var json = mapper.valueToTree(user);
        assertTrue(json.has("householdId"));
        assertTrue(json.get("householdId").isNull());
        user.setUsername(null);
        assertTrue(validator.validate(user).stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test void optionalNullableRequestAcceptsMissingNullAndValues() throws Exception {
        String common = "\"name\":\"Soup\",\"steps\":[\"Cook\"],\"ingredients\":[{\"ingredientId\":\"7e0dd3fb-30d6-4df5-b495-fd067610db79\",\"quantity\":\"1\",\"unit\":\"G\",\"trackStock\":true}],\"yieldServings\":\"1\",\"durationMinutes\":1,\"tags\":[]";
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        for (String suffix : new String[]{"", ",\"imageId\":null,\"totalKcal\":null", ",\"imageId\":\"7e0dd3fb-30d6-4df5-b495-fd067610db79\",\"totalKcal\":\"100\""}) {
            CreateRecipeRequest request = mapper.readValue("{" + common + suffix + "}", CreateRecipeRequest.class);
            assertTrue(validator.validate(request).isEmpty());
        }
        assertThrows(Exception.class, () -> mapper.readValue("{" + common + ",\"unexpected\":true}", CreateRecipeRequest.class));
    }

    @Test void fixturesMatchGeneratedModels() throws Exception {
        Path fixtures = Path.of(System.getProperty("civa.fixtures"));
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        RegisterRequest register = mapper.readValue(Files.readString(fixtures.resolve("register-request.json")), RegisterRequest.class);
        assertTrue(validator.validate(register).isEmpty());
        register.setDisplayName("😀".repeat(120));
        assertTrue(validator.validate(register).isEmpty());
        register.setDisplayName("😀".repeat(121));
        assertTrue(validator.validate(register).stream().anyMatch(v -> v.getPropertyPath().toString().equals("displayName")));
        register.setDisplayName("Alice");
        register.setUsername("\u212Aey"); // Locale.ROOT lowercases the Kelvin sign to ASCII k.
        assertTrue(validator.validate(register).isEmpty());
        register.setPassword("short");
        assertTrue(validator.validate(register).stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
        User user = mapper.readValue(Files.readString(fixtures.resolve("user-response.json")), User.class);
        assertTrue(validator.validate(user).isEmpty());
        assertNull(user.getHouseholdId());
        ApiError error = mapper.readValue(Files.readString(fixtures.resolve("error-response.json")), ApiError.class);
        assertEquals(1, error.getFieldErrors().size());
    }
}
