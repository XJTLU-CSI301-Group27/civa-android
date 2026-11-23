package edu.xjtlu.civa.server.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.xjtlu.civa.server.CivaServer;
import edu.xjtlu.civa.server.api.model.*;
import edu.xjtlu.civa.server.application.CanonicalRequestDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Real HTTP, authentication, PostgreSQL and shared transaction/idempotency integration. */
@SpringBootTest(classes = CivaServer.class)
@AutoConfigureMockMvc
class InventoryHttpIntegrationTest {
  @Autowired MockMvc http;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired InventoryService inventory;
  @Autowired InventoryMatchingService matching;
  @Autowired CanonicalRequestDigest digest;
  @Autowired JsonNode schemas;
  UUID owner, outsider, home, otherHome, ingredient, inactive, recipe;
  String token, otherToken, alias;
  LocalDate today;
  final java.util.Set<UUID> createdIngredients = new java.util.LinkedHashSet<>();

  @BeforeEach
  void fixtures() throws Exception {
    owner = UUID.randomUUID();
    outsider = UUID.randomUUID();
    home = UUID.randomUUID();
    otherHome = UUID.randomUUID();
    ingredient = UUID.randomUUID();
    inactive = UUID.randomUUID();
    recipe = UUID.randomUUID();
    token = registerAndLogin(owner);
    otherToken = registerAndLogin(outsider);
    for (UUID id : List.of(home, otherHome))
      jdbc.update(
          "INSERT INTO households(id,name,zone_id) VALUES (?,'Inventory test','Asia/Shanghai')",
          id);
    jdbc.update(
        "INSERT INTO memberships(id,household_id,user_id,role,active) VALUES (?,?,?,'OWNER',true)",
        UUID.randomUUID(),
        home,
        owner);
    jdbc.update(
        "INSERT INTO memberships(id,household_id,user_id,role,active) VALUES (?,?,?,'OWNER',true)",
        UUID.randomUUID(),
        otherHome,
        outsider);
    alias = "西红柿_" + ingredient;
    jdbc.update(
        "INSERT INTO ingredients(id,name,unit,active) VALUES (?,?,'G',true)",
        ingredient,
        "番茄_" + ingredient);
    jdbc.update(
        "INSERT INTO ingredients(id,name,unit,active) VALUES (?,?,'G',false)",
        inactive,
        "inactive_" + inactive);
    jdbc.update(
        "INSERT INTO ingredient_aliases(normalized_name,ingredient_id,display_name) VALUES (?,?,?)",
        IngredientNames.normalize(alias),
        ingredient,
        alias);
    jdbc.update(
        "INSERT INTO ingredient_aliases(normalized_name,ingredient_id,display_name) VALUES (?,?,?)",
        IngredientNames.normalize("番茄_" + ingredient),
        ingredient,
        "番茄_" + ingredient);
    jdbc.update(
        "INSERT INTO ingredient_aliases(normalized_name,ingredient_id,display_name) VALUES (?,?,?)",
        "inactive_" + inactive,
        inactive,
        "inactive_" + inactive);
    today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
  }

  @AfterEach
  void cleanup() {
    // Only this test's UUID-owned fixtures; never truncate shared tables or clean a database.
    for (UUID id : List.of(home, otherHome)) {
      jdbc.update("DELETE FROM inventory_movements WHERE household_id=?", id);
      jdbc.update("DELETE FROM inventory_batches WHERE household_id=?", id);
      jdbc.update(
          "DELETE FROM recipe_ingredients WHERE recipe_id IN (SELECT id FROM recipes WHERE"
              + " household_id=?)",
          id);
      jdbc.update("DELETE FROM recipes WHERE household_id=?", id);
      jdbc.update("DELETE FROM memberships WHERE household_id=?", id);
      jdbc.update("DELETE FROM households WHERE id=?", id);
    }
    createdIngredients.addAll(List.of(ingredient, inactive));
    for (UUID id : createdIngredients) {
      jdbc.update("DELETE FROM ingredient_aliases WHERE ingredient_id=?", id);
      jdbc.update("DELETE FROM ingredients WHERE id=?", id);
    }
    for (UUID id : List.of(owner, outsider)) {
      jdbc.update("DELETE FROM idempotency_requests WHERE actor_id=?", id);
      jdbc.update("DELETE FROM sessions WHERE user_id=?", id);
      jdbc.update("DELETE FROM preferences WHERE user_id=?", id);
      jdbc.update("DELETE FROM users WHERE id=?", id);
    }
  }

  @Test
  void canonicalIngredientsSearchAliasesAndValidatePagination() throws Exception {
    http.perform(get("/api/v1/ingredients")).andExpect(status().isUnauthorized());
    http.perform(auth(get("/api/v1/ingredients").param("q", "  " + alias + "  ")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value(ingredient.toString()))
        .andExpect(jsonPath("$.items[0].aliases.length()").value(2));
    http.perform(auth(get("/api/v1/ingredients").param("q", "inactive_" + inactive)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    http.perform(
            auth(
                get("/api/v1/ingredients").param("q", alias).param("page", "1").param("size", "1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.total").value(1));
    http.perform(auth(get("/api/v1/ingredients").param("size", "101")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void typedNewEnglishIngredientCanBeCreatedReusedAndSavedAsInventory() throws Exception {
    String name = "quinoa_" + UUID.randomUUID();
    String body = json.writeValueAsString(Map.of("name", name, "unit", "G"));
    UUID key = UUID.randomUUID();
    JsonNode made = ingredientRequest(token, key, body, 201);
    UUID id = UUID.fromString(made.path("id").asText());
    createdIngredients.add(id);
    assertEquals("G", made.path("unit").asText());
    assertEquals(id.toString(), ingredientRequest(token, key, body, 201).path("id").asText());
    assertEquals(
        id.toString(),
        ingredientRequest(
                token,
                UUID.randomUUID(),
                json.writeValueAsString(
                    Map.of(
                        "name",
                        "  " + name.toUpperCase(java.util.Locale.ROOT) + "  ",
                        "unit",
                        "G")),
                200)
            .path("id")
            .asText());
    ingredientRequest(
        token, key, json.writeValueAsString(Map.of("name", name, "unit", "COUNT")), 409);
    http.perform(
            auth(post("/api/v1/ingredients"))
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", name, "unit", "COUNT"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("UNIT_CONFLICT"));
    http.perform(
            auth(post(path()))
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "ingredientId",
                            id,
                            "quantity",
                            "250",
                            "unit",
                            "G",
                            "location",
                            "冷藏 · 蔬菜层"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ingredientId").value(id.toString()))
        .andExpect(jsonPath("$.quantity").value("250"));
    assertEquals(
        1,
        jdbc.queryForObject("SELECT count(*) FROM ingredients WHERE name=?", Integer.class, name));
    assertEquals(
        "250.000",
        jdbc.queryForObject(
            "SELECT quantity::text FROM inventory_batches WHERE ingredient_id=?",
            String.class,
            id));
    http.perform(auth(get("/api/v1/ingredients").param("q", name)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(id.toString()));
  }

  @Test
  void creatingByExistingAliasNeverChangesItsUnitAndRechecksMembership() throws Exception {
    JsonNode reused =
        ingredientRequest(
            token,
            UUID.randomUUID(),
            json.writeValueAsString(Map.of("name", alias, "unit", "G")),
            200);
    assertEquals(ingredient.toString(), reused.path("id").asText());
    ingredientRequest(
        token,
        UUID.randomUUID(),
        json.writeValueAsString(Map.of("name", alias, "unit", "COUNT")),
        409);
    ingredientRequest(
        token,
        UUID.randomUUID(),
        json.writeValueAsString(Map.of("name", "inactive_" + inactive, "unit", "G")),
        409);
    http.perform(
            post("/api/v1/ingredients")
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"food\",\"unit\":\"G\"}"))
        .andExpect(status().isUnauthorized());
    ingredientRequest(token, UUID.randomUUID(), "{\"name\":\"   \",\"unit\":\"G\"}", 400);
    UUID key = UUID.randomUUID();
    String body = json.writeValueAsString(Map.of("name", alias, "unit", "G"));
    ingredientRequest(token, key, body, 200);
    jdbc.update("UPDATE memberships SET active=false WHERE user_id=?", owner);
    http.perform(
            auth(post("/api/v1/ingredients"))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("HOUSEHOLD_REQUIRED"));
    assertEquals(
        "G",
        jdbc.queryForObject("SELECT unit FROM ingredients WHERE id=?", String.class, ingredient));
  }

  @Test
  void concurrentIngredientCreationAcrossHouseholdsProducesOneStandardId() throws Exception {
    String name = "grain_" + UUID.randomUUID();
    String body = json.writeValueAsString(Map.of("name", name, "unit", "G"));
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      java.util.List<java.util.concurrent.Future<JsonNode>> results = new java.util.ArrayList<>();
      for (String credential : List.of(token, otherToken))
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  var response =
                      http.perform(
                              post("/api/v1/ingredients")
                                  .header("Authorization", "Bearer " + credential)
                                  .header("Idempotency-Key", UUID.randomUUID())
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .content(body))
                          .andReturn()
                          .getResponse();
                  assertTrue(response.getStatus() == 200 || response.getStatus() == 201);
                  return json.readTree(response.getContentAsString());
                }));
      start.countDown();
      JsonNode first = results.get(0).get(10, TimeUnit.SECONDS),
          second = results.get(1).get(10, TimeUnit.SECONDS);
      createdIngredients.add(UUID.fromString(first.path("id").asText()));
      createdIngredients.add(UUID.fromString(second.path("id").asText()));
      assertEquals(first.path("id"), second.path("id"));
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT count(*) FROM ingredients WHERE name=?", Integer.class, name));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void canonicalNameSearchWorksWithoutADuplicateAliasRow() throws Exception {
    String name = "番茄_" + ingredient;
    jdbc.update(
        "DELETE FROM ingredient_aliases WHERE ingredient_id=? AND normalized_name=?",
        ingredient,
        IngredientNames.normalize(name));
    http.perform(auth(get("/api/v1/ingredients").param("q", name)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(ingredient.toString()));
    assertEquals(
        ingredient.toString(),
        ingredientRequest(
                token,
                UUID.randomUUID(),
                json.writeValueAsString(Map.of("name", name, "unit", "G")),
                200)
            .path("id")
            .asText());
  }

  private JsonNode ingredientRequest(String credential, UUID key, String body, int expected)
      throws Exception {
    var response =
        http.perform(
                post("/api/v1/ingredients")
                    .header("Authorization", "Bearer " + credential)
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse();
    return json.readTree(response.getContentAsString());
  }

  @Test
  void createsSeparateBatchesReplaysRequestsAndRejectsBadUnits() throws Exception {
    String body = createBody("2.000", today.plusDays(1));
    UUID key = UUID.randomUUID();
    JsonNode first = create(key, body);
    assertEquals("2", first.path("quantity").asText());
    assertEquals(first.path("id"), create(key, body).path("id"));
    create(UUID.randomUUID(), createBody("3", today.plusDays(5)));
    http.perform(auth(get(path())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2));
    http.perform(
            auth(post(path()))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("9", today)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    for (String bad :
        List.of(
            createBody("2", today).replace("\"G\"", "\"ML\""),
            createBody("-1", today),
            createBody("1", today).replace(ingredient.toString(), inactive.toString()),
            createBody("1", today).replace(today.toString(), "2025-02-29")))
      http.perform(
              auth(post(path()))
                  .header("Idempotency-Key", UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(bad))
          .andExpect(status().isBadRequest());
    http.perform(auth(post(path())).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void expiryFiltersIncludeEndpointsUnknownDatesAndStablePages() throws Exception {
    create(UUID.randomUUID(), createBody("1", today.minusDays(1)));
    create(UUID.randomUUID(), createBody("1", today));
    create(UUID.randomUUID(), createBody("1", today.plusDays(3)));
    create(UUID.randomUUID(), createBody("1", today.plusDays(4)));
    create(UUID.randomUUID(), createBody("1", null));
    for (var expected : Map.of("ALL", 5, "VALID", 4, "SOON", 2, "EXPIRED", 1).entrySet())
      http.perform(auth(get(path()).param("expiryStatus", expected.getKey())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total").value(expected.getValue()));
    http.perform(auth(get(path()).param("page", "0").param("size", "1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].expiresOn").value(today.minusDays(1).toString()))
        .andExpect(jsonPath("$.total").value(5));
    http.perform(auth(get(path()).param("page", "4").param("size", "1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].expiresOn").doesNotExist());
  }

  @Test
  void metadataChangesCheckVersionsAndNeverChangeQuantity() throws Exception {
    String id = create(UUID.randomUUID(), createBody("4", today)).path("id").asText();
    String update = "{\"location\":\"冷冻\",\"expiresOn\":null,\"expectedVersion\":1}";
    http.perform(
            auth(put(path() + "/" + id)).contentType(MediaType.APPLICATION_JSON).content(update))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.quantity").value("4"))
        .andExpect(jsonPath("$.location").value("冷冻"));
    http.perform(
            auth(put(path() + "/" + id)).contentType(MediaType.APPLICATION_JSON).content(update))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    http.perform(
            auth(put(path() + "/" + id))
                .contentType(MediaType.APPLICATION_JSON)
                .content(update.replace("{", "{\"quantity\":\"999\",")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void quantityChangesAreTraceableIdempotentAndNeverNegative() throws Exception {
    String id = create(UUID.randomUUID(), createBody("4", today)).path("id").asText();
    UUID key = UUID.randomUUID();
    String consume = changeBody("3", "CONSUME", 1);
    JsonNode first = change(id, key, consume, 201);
    assertEquals(first.path("id"), change(id, key, consume, 201).path("id"));
    assertEquals("4", first.path("beforeQuantity").asText());
    assertEquals(key.toString(), first.path("requestId").asText());
    change(id, UUID.randomUUID(), changeBody("2", "DISCARD", 1), 409);
    change(id, UUID.randomUUID(), changeBody("-1", "DISCARD", 2), 400);
    change(id, UUID.randomUUID(), changeBody("4", "CONSUME", 2), 400);
    change(id, UUID.randomUUID(), changeBody("3", "DISCARD", 2), 400);
    change(id, UUID.randomUUID(), changeBody("2", "DISCARD", 2), 201);
    change(id, UUID.randomUUID(), changeBody("5", "CORRECT", 3), 201);
    http.perform(
            auth(get("/api/v1/households/" + home + "/inventory-movements").param("batchId", id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3));
    assertEquals(
        "5.000",
        jdbc.queryForObject(
            "SELECT quantity::text FROM inventory_batches WHERE id=?",
            String.class,
            UUID.fromString(id)));
  }

  @Test
  void householdBoundariesAndRevokedReplayAreEnforced() throws Exception {
    UUID key = UUID.randomUUID();
    String body = createBody("4", today);
    String id = create(key, body).path("id").asText();
    http.perform(get(path()).header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound());
    http.perform(
            get("/api/v1/households/" + home + "/inventory-movements")
                .header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound());
    http.perform(
            put(path() + "/" + id)
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"location\":\"x\",\"expiresOn\":null,\"expectedVersion\":1}"))
        .andExpect(status().isNotFound());
    http.perform(
            auth(put("/api/v1/households/" + otherHome + "/inventory-batches/" + id))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"location\":\"x\",\"expiresOn\":null,\"expectedVersion\":1}"))
        .andExpect(status().isNotFound());
    jdbc.update("UPDATE memberships SET active=false WHERE user_id=?", owner);
    http.perform(auth(get(path())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("HOUSEHOLD_ACCESS_REVOKED"));
    http.perform(
            auth(post(path()))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void matchingAndAllocationUseRealStockAndNeverWrite() throws Exception {
    jdbc.update(
        "INSERT INTO recipes(id,household_id,author_id,name,steps,yield_servings,duration_minutes)"
            + " VALUES (?,?,?,'Test recipe','[\"test\"]',2,10)",
        recipe,
        home,
        owner);
    jdbc.update(
        "INSERT INTO recipe_ingredients(recipe_id,ingredient_id,position,quantity,unit,track_stock)"
            + " VALUES (?,?,0,4,'G',true)",
        recipe,
        ingredient);
    create(UUID.randomUUID(), createBody("100", today.minusDays(1)));
    String first = create(UUID.randomUUID(), createBody("1", today)).path("id").asText();
    String second =
        create(UUID.randomUUID(), createBody("2", today.plusDays(3))).path("id").asText();
    create(UUID.randomUUID(), createBody("0", null));
    String request =
        json.writeValueAsString(
            Map.of("entries", List.of(Map.of("recipeId", recipe, "servings", "2"))));
    String matchPath = "/api/v1/households/" + home + "/recipe-matches";
    http.perform(auth(post(matchPath)).contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].coverage").value("0"))
        .andExpect(jsonPath("$[0].lines[0].required").value("4"))
        .andExpect(jsonPath("$[0].lines[0].available").value("3"))
        .andExpect(jsonPath("$[0].lines[0].status").value("MISSING"));
    CookingPreview preview = matching.preview(owner, home, recipe, "1");
    assertTrue(preview.getShortages().isEmpty());
    assertEquals(
        List.of(UUID.fromString(first), UUID.fromString(second)),
        preview.getAllocations().stream().map(CookingAllocation::getBatchId).toList());
    assertEquals(
        "2",
        preview.getAllocations().stream()
            .map(CookingAllocation::getQuantity)
            .map(java.math.BigDecimal::new)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
            .toPlainString());
    assertEquals(1, matching.preview(owner, home, recipe, "2").getShortages().size());
    assertEquals(
        0L,
        jdbc.queryForObject(
            "SELECT count(*) FROM inventory_movements WHERE household_id=?", Long.class, home));
    assertEquals(
        "1.000",
        jdbc.queryForObject(
            "SELECT quantity::text FROM inventory_batches WHERE id=?",
            String.class,
            UUID.fromString(first)));
    http.perform(
            post(matchPath)
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isNotFound());
    String duplicate =
        json.writeValueAsString(
            Map.of(
                "entries",
                List.of(
                    Map.of("recipeId", recipe, "servings", "1"),
                    Map.of("recipeId", recipe, "servings", "2"))));
    http.perform(auth(post(matchPath)).contentType(MediaType.APPLICATION_JSON).content(duplicate))
        .andExpect(status().isBadRequest());
  }

  @Test
  void concurrentVersionedChangesCannotOverwriteEachOther() throws Exception {
    UUID id =
        UUID.fromString(create(UUID.randomUUID(), createBody("3", today)).path("id").asText());
    var pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      var tasks =
          List.of("1", "2").stream()
              .map(
                  after ->
                      pool.submit(
                          () -> {
                            start.await();
                            var body =
                                new InventoryChangeRequest()
                                    .afterQuantity(after)
                                    .reason(ChangeReason.CONSUME)
                                    .expectedVersion(1L);
                            byte[] hash =
                                digest.digestJson(
                                    "POST",
                                    path() + "/" + id + "/movements",
                                    json.writeValueAsString(body),
                                    schemas.get("InventoryChangeRequest"));
                            try {
                              inventory.change(owner, home, id, UUID.randomUUID(), hash, body);
                              return "OK";
                            } catch (InventoryFailure error) {
                              return error.code;
                            }
                          }))
              .toList();
      start.countDown();
      List<String> outcomes =
          List.of(tasks.get(0).get(20, TimeUnit.SECONDS), tasks.get(1).get(20, TimeUnit.SECONDS));
      assertEquals(1, outcomes.stream().filter("OK"::equals).count());
      assertEquals(1, outcomes.stream().filter("VERSION_CONFLICT"::equals).count());
      assertEquals(
          1L,
          jdbc.queryForObject(
              "SELECT count(*) FROM inventory_movements WHERE household_id=?", Long.class, home));
    } finally {
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  @Test
  void concurrentIdenticalRequestsOnlyDeductOnce() throws Exception {
    UUID id =
        UUID.fromString(create(UUID.randomUUID(), createBody("3", today)).path("id").asText());
    UUID key = UUID.randomUUID();
    var body =
        new InventoryChangeRequest()
            .afterQuantity("1")
            .reason(ChangeReason.CONSUME)
            .expectedVersion(1L);
    byte[] hash =
        digest.digestJson(
            "POST",
            path() + "/" + id + "/movements",
            json.writeValueAsString(body),
            schemas.get("InventoryChangeRequest"));
    var pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      var a =
          pool.submit(
              () -> {
                start.await();
                return inventory.change(owner, home, id, key, hash, body).body;
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return inventory.change(owner, home, id, key, hash, body).body;
              });
      start.countDown();
      assertEquals(
          json.readTree(a.get(20, TimeUnit.SECONDS)), json.readTree(b.get(20, TimeUnit.SECONDS)));
      assertEquals(
          1L,
          jdbc.queryForObject(
              "SELECT count(*) FROM inventory_movements WHERE household_id=?", Long.class, home));
      assertEquals(
          2L,
          jdbc.queryForObject("SELECT version FROM inventory_batches WHERE id=?", Long.class, id));
    } finally {
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  @Test
  void aForeignBatchCannotBeChangedThroughAnAuthorizedHousehold() throws Exception {
    UUID foreign = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO inventory_batches(id,household_id,ingredient_id,quantity,unit,location) VALUES"
            + " (?,?,?,7,'G','other')",
        foreign,
        otherHome,
        ingredient);
    http.perform(auth(get(path())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    change(foreign.toString(), UUID.randomUUID(), changeBody("1", "CONSUME", 1), 404);
    assertEquals(
        "7.000",
        jdbc.queryForObject(
            "SELECT quantity::text FROM inventory_batches WHERE id=?", String.class, foreign));
  }

  @Test
  void lockTimeoutIsRetryableAndDoesNotCommitAnIdempotencyPlaceholder() throws Exception {
    UUID key = UUID.randomUUID();
    String body = createBody("1", today);
    try (var connection = jdbc.getDataSource().getConnection()) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement("SELECT id FROM households WHERE id=? FOR UPDATE")) {
        statement.setObject(1, home);
        statement.executeQuery().close();
        http.perform(
                auth(post(path()))
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "1"))
            .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"));
      } finally {
        connection.rollback();
      }
    }
    assertEquals(
        0L,
        jdbc.queryForObject(
            "SELECT count(*) FROM idempotency_requests WHERE actor_id=? AND key=?",
            Long.class,
            owner,
            key));
    create(key, body);
  }

  private String registerAndLogin(UUID id) throws Exception {
    String name = "inv_" + id.toString().replace("-", "").substring(0, 20);
    http.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "username",
                            name,
                            "displayName",
                            "Inventory test",
                            "password",
                            "inventory-test-password",
                            "zoneId",
                            "Asia/Shanghai"))))
        .andExpect(status().isCreated());
    // Reuse the server-generated identity, not the UUID used to make the username unique.
    UUID actual = jdbc.queryForObject("SELECT id FROM users WHERE username=?", UUID.class, name);
    if (id.equals(owner)) owner = actual;
    else outsider = actual;
    var result =
        http.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of("username", name, "password", "inventory-test-password"))))
            .andExpect(status().isOk())
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("token").asText();
  }

  private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
    return request.header("Authorization", "Bearer " + token);
  }

  private String path() {
    return "/api/v1/households/" + home + "/inventory-batches";
  }

  private String createBody(String quantity, LocalDate expiry) throws Exception {
    var body =
        new CreateInventoryBatchRequest()
            .ingredientId(ingredient)
            .quantity(quantity)
            .unit(Unit.G)
            .location("冷藏")
            .expiresOn(expiry == null ? null : expiry.toString());
    return json.writeValueAsString(body);
  }

  private JsonNode create(UUID key, String body) throws Exception {
    var result =
        http.perform(
                auth(post(path()))
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private String changeBody(String after, String reason, long version) throws Exception {
    return json.writeValueAsString(
        Map.of("afterQuantity", after, "reason", reason, "expectedVersion", version));
  }

  private JsonNode change(String id, UUID key, String body, int expected) throws Exception {
    var result =
        http.perform(
                auth(post(path() + "/" + id + "/movements"))
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().is(expected))
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }
}
