package edu.xjtlu.civa.core.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.xjtlu.civa.api.model.MembershipPage;
import edu.xjtlu.civa.api.model.Preference;
import edu.xjtlu.civa.api.model.Recipe;
import edu.xjtlu.civa.api.model.User;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class DtoChecksTest {
    @Test public void validatesDatesBoundsUniqueItemsAndNestedReferences() throws Exception {
        JsonObject schemas;
        try (Reader reader = Files.newBufferedReader(Path.of(System.getProperty("civa.contract")))) {
            schemas = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("components").getAsJsonObject("schemas");
        }
        String id = "7e0dd3fb-30d6-4df5-b495-fd067610db79";
        String time = "2024-02-29T23:59:59.123Z";
        String userJson = "{\"id\":\"" + id + "\",\"createdAt\":\"" + time + "\",\"updatedAt\":\"" + time
                + "\",\"username\":\"alice\",\"displayName\":\"Alice\",\"zoneId\":\"UTC\",\"householdId\":null}";
        assertTrue(DtoChecks.valid(ApiCalls.GSON.fromJson(userJson, User.class), schemas));
        assertFalse(DtoChecks.valid(ApiCalls.GSON.fromJson(userJson.replace("2024-02-29", "2024-02-30"), User.class), schemas));
        assertFalse(DtoChecks.valid(ApiCalls.GSON.fromJson(userJson.replace("23:59:59", "23:59:60"), User.class), schemas));
        assertFalse(DtoChecks.valid(ApiCalls.GSON.fromJson(userJson.replace("Alice", ""), User.class), schemas));

        String preference = "{\"userId\":\"" + id + "\",\"likedTags\":[\"soup\"],\"excludedIngredientIds\":[],\"version\":1}";
        assertTrue(DtoChecks.valid(ApiCalls.GSON.fromJson(preference, Preference.class), schemas));
        String duplicates = preference.replace("[\"soup\"]", "[\"soup\",\"soup\"]");
        assertFalse(DtoChecks.validTree(JsonParser.parseString(duplicates), schemas.getAsJsonObject("Preference"), schemas));
        // Gson's generated Set loses duplicates before DTO validation; raw responses still need validation.
        assertTrue(DtoChecks.valid(ApiCalls.GSON.fromJson(duplicates, Preference.class), schemas));
        assertFalse(DtoChecks.valid(ApiCalls.GSON.fromJson(preference.replace("\"version\":1", "\"version\":-1"), Preference.class), schemas));

        String member = "{\"id\":\"" + id + "\",\"createdAt\":\"" + time + "\",\"updatedAt\":\"" + time
                + "\",\"householdId\":\"" + id + "\",\"userId\":\"" + id + "\",\"role\":\"MEMBER\",\"active\":true,\"version\":1}";
        String page = "{\"items\":[" + member + "],\"page\":0,\"size\":20,\"total\":1}";
        assertTrue(DtoChecks.valid(ApiCalls.GSON.fromJson(page, MembershipPage.class), schemas));
        assertFalse(DtoChecks.valid(ApiCalls.GSON.fromJson(page.replace("\"version\":1", "\"version\":-1"), MembershipPage.class), schemas));

        String recipe = "{\"id\":\"" + id + "\",\"createdAt\":\"" + time + "\",\"updatedAt\":\"" + time
                + "\",\"householdId\":\"" + id + "\",\"authorId\":\"" + id + "\",\"name\":\"Soup\","
                + "\"steps\":[\"Cook\"],\"ingredients\":[{\"ingredientId\":\"" + id
                + "\",\"quantity\":\"1.25\",\"unit\":\"G\",\"trackStock\":true}],"
                + "\"yieldServings\":\"1\",\"durationMinutes\":20,\"tags\":[],\"imageId\":null,\"totalKcal\":null,\"archived\":false,\"version\":1}";
        assertTrue(DtoChecks.valid(ApiCalls.GSON.fromJson(recipe, Recipe.class), schemas));
        assertFalse(DtoChecks.valid(ApiCalls.GSON.fromJson(recipe.replace("1.25", "1.2345"), Recipe.class), schemas));
        assertFalse(DtoChecks.valid(ApiCalls.GSON.fromJson(recipe.replace("\"durationMinutes\":20", "\"durationMinutes\":1441"), Recipe.class), schemas));
    }
}
