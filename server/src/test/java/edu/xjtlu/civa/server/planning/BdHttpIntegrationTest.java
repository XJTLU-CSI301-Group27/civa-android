package edu.xjtlu.civa.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.xjtlu.civa.server.CivaServer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(classes = CivaServer.class)
@AutoConfigureMockMvc
class BdHttpIntegrationTest {
  @Autowired MockMvc http;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired PasswordEncoder passwords;
  UUID actor, other, home, recipe;
  String token, otherToken, date;

  @BeforeEach
  void setup() throws Exception {
    actor = UUID.randomUUID();
    other = UUID.randomUUID();
    home = UUID.randomUUID();
    recipe = UUID.randomUUID();
    date = LocalDate.now(ZoneOffset.UTC).toString();
    for (UUID id : List.of(actor, other))
      jdbc.update(
          "INSERT INTO users(id,username,display_name,password_hash,zone_id) VALUES (?,?,?,?,?)",
          id,
          "b" + id.toString().replace("-", "").substring(0, 20),
          "BD Test",
          passwords.encode("bd-test-password"),
          "UTC");
    jdbc.update("INSERT INTO households(id,name) VALUES (?,?)", home, "BD isolated test");
    jdbc.update(
        "INSERT INTO memberships(id,household_id,user_id,role,active) VALUES (?,?,?,'OWNER',true)",
        UUID.randomUUID(),
        home,
        actor);
    jdbc.update(
        "INSERT INTO memberships(id,household_id,user_id,role,active) VALUES (?,?,?,'MEMBER',true)",
        UUID.randomUUID(),
        home,
        other);
    jdbc.update(
        "INSERT INTO"
            + " recipes(id,household_id,author_id,name,steps,yield_servings,duration_minutes,total_kcal)"
            + " VALUES (?,?,?,'Original dish','[\"Cook\"]'::jsonb,2,20,600)",
        recipe,
        home,
        actor);
    token = login(actor);
    otherToken = login(other);
  }

  @AfterEach
  void cleanup() {
    if (home == null) return;
    jdbc.update("DELETE FROM idempotency_requests WHERE actor_id IN (?,?)", actor, other);
    jdbc.update("DELETE FROM meal_entries WHERE user_id IN (?,?)", actor, other);
    jdbc.update("DELETE FROM plan_items WHERE household_id=?", home);
    jdbc.update("DELETE FROM plan_slots WHERE household_id=?", home);
    jdbc.update("DELETE FROM recipes WHERE household_id=?", home);
    jdbc.update("DELETE FROM memberships WHERE household_id=?", home);
    jdbc.update("DELETE FROM households WHERE id=?", home);
    jdbc.update("DELETE FROM sessions WHERE user_id IN (?,?)", actor, other);
    jdbc.update("DELETE FROM users WHERE id IN (?,?)", actor, other);
  }

  @Test
  void planIsSeparateFromRecordsAndLockedSlotsRejectWrites() throws Exception {
    String path = slot(date, "LUNCH");
    call(put(path), token, Map.of("items", items(), "expectedVersion", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    call(get("/api/v1/me/meal-entries").param("from", date).param("to", date), token, null)
        .andExpect(jsonPath("$.total").value(0));
    call(put(path + "/lock"), token, Map.of("locked", true, "expectedVersion", 1))
        .andExpect(status().isOk());
    call(put(path), token, Map.of("items", List.of(), "expectedVersion", 2))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PLAN_LOCKED"));
    call(put(path + "/lock"), token, Map.of("locked", false, "expectedVersion", 2))
        .andExpect(status().isOk());
    call(put(path), token, Map.of("items", List.of(), "expectedVersion", 3))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void commitsReplayAndBatchConflictsRollbackAllChanges() throws Exception {
    String tomorrow = LocalDate.parse(date).plusDays(1).toString();
    UUID key = UUID.randomUUID();
    Map<String, Object> body = Map.of("slots", List.of(write(date, 0), write(tomorrow, 0)));
    call(post(commit()).header("Idempotency-Key", key), token, body)
        .andExpect(status().isOk());
    call(post(commit()).header("Idempotency-Key", key), token, body)
        .andExpect(status().isOk());
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT count(*) FROM plan_slots WHERE household_id=?", Integer.class, home));
    call(
            post(commit()).header("Idempotency-Key", key),
            token,
            Map.of("slots", List.of(write(date, 1))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    call(
            post(commit()).header("Idempotency-Key", UUID.randomUUID()),
            token,
            Map.of("slots", List.of(write(date, 1), write(tomorrow, 0))))
        .andExpect(status().isConflict());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT version FROM plan_slots WHERE household_id=? AND date=?::date",
            Integer.class,
            home,
            date));
  }

  @Test
  void concurrentPlanUpdatesHaveOneWinner() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Callable<Integer> work =
          () -> {
            start.await();
            return call(
                    put(slot(date, "LUNCH")), token, Map.of("items", items(), "expectedVersion", 0))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      Future<Integer> first = pool.submit(work), second = pool.submit(work);
      start.countDown();
      List<Integer> results =
          new ArrayList<>(
              List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)));
      Collections.sort(results);
      assertEquals(List.of(200, 409), results);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void personalMealsReplayPreserveSnapshotsAndSummarizeUnknowns() throws Exception {
    UUID key = UUID.randomUUID();
    Map<String, Object> meal = new HashMap<>(meal("GRAM", "150", "200"));
    meal.put("recipeId", recipe.toString());
    var response =
        call(post("/api/v1/me/meal-entries").header("Idempotency-Key", key), token, meal)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.totalKcal").value("300"))
            .andReturn();
    String id = json.readTree(response.getResponse().getContentAsString()).path("id").asText();
    call(post("/api/v1/me/meal-entries").header("Idempotency-Key", key), token, meal)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(id));
    call(
            post("/api/v1/me/meal-entries").header("Idempotency-Key", UUID.randomUUID()),
            token,
            meal("UNKNOWN", "1", null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.totalKcal").isEmpty());
    jdbc.update("UPDATE recipes SET name='Changed dish',total_kcal=999 WHERE id=?", recipe);
    call(get("/api/v1/me/meal-entries").param("from", date).param("to", date), token, null)
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items[0].name").value("Lunch snapshot"));
    call(
            get("/api/v1/me/meal-summary")
                .param("from", LocalDate.parse(date).minusDays(2).toString())
                .param("to", date),
            token,
            null)
        .andExpect(jsonPath("$.knownKcal").value("300"))
        .andExpect(jsonPath("$.unknownCount").value(1))
        .andExpect(jsonPath("$.averageKnownKcal").value("100"))
        .andExpect(jsonPath("$.days.length()").value(3));
    call(get("/api/v1/me/meal-entries").param("from", date).param("to", date), otherToken, null)
        .andExpect(jsonPath("$.total").value(0));
    call(delete("/api/v1/me/meal-entries/" + id).param("expectedVersion", "1"), otherToken, null)
        .andExpect(status().isNotFound());
    Map<String, Object> edit = new HashMap<>(meal);
    edit.put("expectedVersion", 0);
    call(put("/api/v1/me/meal-entries/" + id), token, edit).andExpect(status().isConflict());
    edit.put("expectedVersion", 1);
    edit.put("amount", "50");
    call(put("/api/v1/me/meal-entries/" + id), token, edit)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalKcal").value("100"));
    call(delete("/api/v1/me/meal-entries/" + id).param("expectedVersion", "2"), token, null)
        .andExpect(status().isNoContent());
  }

  @Test
  void invalidDatesAndCaloriesAreRejectedAndRevokedAccessIsDenied() throws Exception {
    var future = new HashMap<>(meal("MANUAL", "1", "100"));
    future.put("date", LocalDate.now(ZoneOffset.UTC).plusDays(1).toString());
    call(
            post("/api/v1/me/meal-entries").header("Idempotency-Key", UUID.randomUUID()),
            token,
            future)
        .andExpect(status().isBadRequest());
    call(
            post("/api/v1/me/meal-entries").header("Idempotency-Key", UUID.randomUUID()),
            token,
            meal("MANUAL", "2", "100"))
        .andExpect(status().isBadRequest());
    call(
            post("/api/v1/me/meal-entries").header("Idempotency-Key", UUID.randomUUID()),
            token,
            meal("UNKNOWN", "1", "0"))
        .andExpect(status().isBadRequest());
    call(
            get("/api/v1/households/" + home + "/plan-slots")
                .param("from", date)
                .param("to", LocalDate.parse(date).plusDays(21).toString()),
            token,
            null)
        .andExpect(status().isBadRequest());
    jdbc.update("UPDATE memberships SET active=false WHERE user_id=?", other);
    call(
            get("/api/v1/households/" + home + "/plan-slots").param("from", date).param("to", date),
            otherToken,
            null)
        .andExpect(status().isForbidden());
    assertEquals("0.001", BdRules.calories("GRAM", "0.5", "0.1"));
  }

  private Map<String, Object> meal(String basis, String amount, String kcal) {
    Map<String, Object> result = new HashMap<>();
    result.put("date", date);
    result.put("mealType", "LUNCH");
    result.put("name", "Lunch snapshot");
    result.put("amount", amount);
    result.put("basis", basis);
    result.put("basisKcal", kcal);
    result.put("note", "");
    return result;
  }

  private Map<String, Object> write(String date, int version) {
    return Map.of("date", date, "mealType", "LUNCH", "expectedVersion", version, "items", items());
  }

  private List<?> items() {
    return List.of(Map.of("recipeId", recipe.toString(), "servings", "1"));
  }

  private String slot(String date, String meal) {
    return "/api/v1/households/" + home + "/plan-slots/" + date + "/" + meal;
  }

  private String commit() {
    return "/api/v1/households/" + home + "/plan-commits";
  }

  private org.springframework.test.web.servlet.ResultActions call(
      MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
    request.header("Authorization", "Bearer " + token);
    if (body != null)
      request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    return http.perform(request);
  }

  private String login(UUID id) throws Exception {
    String username = "b" + id.toString().replace("-", "").substring(0, 20);
    String body =
        http.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of("username", username, "password", "bd-test-password"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).path("token").asText();
  }
}
