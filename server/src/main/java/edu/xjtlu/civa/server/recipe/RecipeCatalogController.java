package edu.xjtlu.civa.server.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.xjtlu.civa.server.api.RecipeApi;
import edu.xjtlu.civa.server.api.model.*;
import edu.xjtlu.civa.server.household.HouseholdAccess;
import edu.xjtlu.civa.server.web.ApiFailure;
import edu.xjtlu.civa.server.web.Paging;
import edu.xjtlu.civa.server.web.WireNumbers;
import java.sql.*;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/** Minimal shared catalog read adapter. AC owns this route: extend it, never add a second one. */
@RestController
class RecipeCatalogController implements RecipeApi {
  private final JdbcTemplate jdbc;
  private final HouseholdAccess access;
  private final ObjectMapper json;
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  RecipeCatalogController(JdbcTemplate jdbc, HouseholdAccess access, ObjectMapper json) {
    this.jdbc = jdbc;
    this.access = access;
    this.json = json;
  }

  @Override
  public ResponseEntity<RecipePage> listRecipes(
      UUID home,
      String q,
      String tag,
      UUID ingredient,
      Boolean archived,
      Integer page,
      Integer size) {
    access.requireMember(actor(), home, false);
    Paging.check(page, size);
    List<Object> params = new ArrayList<>();
    params.add(home);
    params.add(Boolean.TRUE.equals(archived));
    String where = " WHERE r.household_id=? AND r.archived=?";
    if (q != null && !q.isBlank()) {
      where += " AND position(lower(?) in lower(r.name))>0";
      params.add(q.trim());
    }
    if (tag != null) {
      where += " AND ?=ANY(r.tags)";
      params.add(tag);
    }
    if (ingredient != null) {
      where +=
          " AND EXISTS(SELECT 1 FROM recipe_ingredients ri WHERE ri.recipe_id=r.id AND"
              + " ri.ingredient_id=?)";
      params.add(ingredient);
    }
    long total =
        jdbc.queryForObject("SELECT count(*) FROM recipes r" + where, Long.class, params.toArray());
    params.add(size);
    params.add((long) page * size);
    List<Recipe> values =
        jdbc.query(
            "SELECT r.* FROM recipes r"
                + where
                + " ORDER BY r.created_at DESC,r.id LIMIT ? OFFSET ?",
            this::map,
            params.toArray());
    return ResponseEntity.ok(new RecipePage().items(values).page(page).size(size).total(total));
  }

  @Override
  public ResponseEntity<Recipe> getRecipe(UUID home, UUID id) {
    access.requireMember(actor(), home, false);
    List<Recipe> values =
        jdbc.query("SELECT * FROM recipes WHERE id=? AND household_id=?", this::map, id, home);
    if (values.isEmpty()) throw new ApiFailure("NOT_FOUND");
    return ResponseEntity.ok(values.get(0));
  }

  @Override
  public ResponseEntity<RecipePage> listWishlist(Integer page, Integer size) {
    Paging.check(page, size);
    UUID actor = actor();
    String where =
        " FROM recipes r JOIN wishlists w ON w.recipe_id=r.id AND w.user_id=? JOIN memberships m ON"
            + " m.household_id=r.household_id AND m.user_id=w.user_id AND m.active";
    long total = jdbc.queryForObject("SELECT count(*)" + where, Long.class, actor);
    List<Recipe> values =
        jdbc.query(
            "SELECT r.*" + where + " ORDER BY w.created_at DESC,r.id LIMIT ? OFFSET ?",
            this::map,
            actor,
            size,
            (long) page * size);
    return ResponseEntity.ok(new RecipePage().items(values).page(page).size(size).total(total));
  }

  private Recipe map(ResultSet rs, int row) throws SQLException {
    UUID id = rs.getObject("id", UUID.class);
    List<RecipeIngredient> ingredients =
        jdbc.query(
            "SELECT * FROM recipe_ingredients WHERE recipe_id=? ORDER BY position",
            (r, n) ->
                new RecipeIngredient()
                    .ingredientId(r.getObject("ingredient_id", UUID.class))
                    .quantity(WireNumbers.number(r.getBigDecimal("quantity")))
                    .unit(Unit.fromValue(r.getString("unit")))
                    .trackStock(r.getBoolean("track_stock")),
            id);
    try {
      java.sql.Array tags = rs.getArray("tags");
      List<String> tagValues = Arrays.asList((String[]) tags.getArray());
      tags.free();
      return new Recipe()
          .id(id)
          .createdAt(TIME.format(rs.getTimestamp("created_at").toInstant()))
          .updatedAt(TIME.format(rs.getTimestamp("updated_at").toInstant()))
          .householdId(rs.getObject("household_id", UUID.class))
          .authorId(rs.getObject("author_id", UUID.class))
          .name(rs.getString("name"))
          .steps(
              json.readValue(
                  rs.getString("steps"),
                  json.getTypeFactory().constructCollectionType(List.class, String.class)))
          .ingredients(ingredients)
          .yieldServings(WireNumbers.number(rs.getBigDecimal("yield_servings")))
          .durationMinutes(rs.getInt("duration_minutes"))
          .tags(new LinkedHashSet<>(tagValues))
          .imageId(rs.getObject("image_id", UUID.class))
          .totalKcal(
              rs.getBigDecimal("total_kcal") == null
                  ? null
                  : WireNumbers.number(rs.getBigDecimal("total_kcal")))
          .archived(rs.getBoolean("archived"))
          .version(rs.getLong("version"));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("Invalid stored recipe", ex);
    }
  }

  @Override
  public ResponseEntity<Void> addWishlist(UUID id) {
    throw unavailable();
  }

  @Override
  public ResponseEntity<Void> removeWishlist(UUID id) {
    throw unavailable();
  }

  @Override
  public ResponseEntity<Recipe> createRecipe(UUID home, UUID key, CreateRecipeRequest body) {
    throw unavailable();
  }

  @Override
  public ResponseEntity<Recipe> updateRecipe(UUID home, UUID id, UpdateRecipeRequest body) {
    throw unavailable();
  }

  @Override
  public ResponseEntity<Recipe> setRecipeArchived(UUID home, UUID id, ArchiveRecipeRequest body) {
    throw unavailable();
  }

  private static ApiFailure unavailable() {
    return new ApiFailure("CONTRACT_NOT_IMPLEMENTED");
  }

  private static UUID actor() {
    return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }
}
