package edu.xjtlu.civa.server.inventory;

import edu.xjtlu.civa.server.api.model.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Internal read-only matching/allocation, in one consistent database snapshot. */
final class InventoryMatchingService {
  private final InventoryService inventory;

  InventoryMatchingService(InventoryService inventory) {
    this.inventory = inventory;
  }

  List<RecipeMatch> match(UUID actor, UUID home, MatchRequest body) {
    if (body == null
        || body.getEntries() == null
        || body.getEntries().isEmpty()
        || body.getEntries().size() > 100)
      throw new IllegalArgumentException("Invalid match entries");
    HashSet<UUID> unique = new HashSet<>();
    for (MatchEntry entry : body.getEntries()) {
      if (entry == null || entry.getRecipeId() == null || !unique.add(entry.getRecipeId()))
        throw new IllegalArgumentException("Duplicate or missing recipe");
      InventoryRules.positive(entry.getServings());
    }
    return inventory.snapshot(
        () -> {
          LocalDate today = inventory.today(inventory.authorize(actor, home, false));
          List<InventoryBatch> stock = stock(home);
          String checkedAt = inventory.checkedAt();
          return body.getEntries().stream()
              .map(
                  entry ->
                      calculate(
                              home,
                              entry.getRecipeId(),
                              entry.getServings(),
                              stock,
                              today,
                              checkedAt)
                          .match())
              .toList();
        });
  }

  // Internal candidate seam only. No cooking write is performed, and no new public API is exported.
  CookingPreview preview(UUID actor, UUID home, UUID recipe, String servings) {
    InventoryRules.positive(servings);
    return inventory.snapshot(
        () -> {
          LocalDate today = inventory.today(inventory.authorize(actor, home, false));
          String checkedAt = inventory.checkedAt();
          var result = calculate(home, recipe, servings, stock(home), today, checkedAt);
          return new CookingPreview()
              .recipeVersion(result.match().getRecipeVersion())
              .servings(result.match().getServings())
              .planSlotId(null)
              .allocations(result.allocations())
              .shortages(result.shortages())
              .checkedAt(checkedAt);
        });
  }

  private List<InventoryBatch> stock(UUID home) {
    return inventory.jdbc.query(
        "SELECT * FROM inventory_batches WHERE household_id=? AND quantity>0 ORDER BY expires_on"
            + " NULLS LAST,created_at,id",
        InventoryService::batch,
        home);
  }

  private InventoryCalculator.Result calculate(
      UUID home,
      UUID recipe,
      String servings,
      List<InventoryBatch> stock,
      LocalDate today,
      String checkedAt) {
    List<Recipe> rows =
        inventory.jdbc.query(
            "SELECT id,version,yield_servings FROM recipes WHERE household_id=? AND id=? AND NOT"
                + " archived",
            (rs, n) ->
                new Recipe()
                    .id(rs.getObject("id", UUID.class))
                    .version(rs.getLong("version"))
                    .yieldServings(InventoryRules.number(rs.getBigDecimal("yield_servings"))),
            home,
            recipe);
    if (rows.isEmpty()) throw new InventoryFailure("NOT_FOUND");
    List<RecipeIngredient> ingredients =
        inventory.jdbc.query(
            "SELECT * FROM recipe_ingredients WHERE recipe_id=? ORDER BY position,ingredient_id",
            (rs, n) ->
                new RecipeIngredient()
                    .ingredientId(rs.getObject("ingredient_id", UUID.class))
                    .unit(Unit.fromValue(rs.getString("unit")))
                    .quantity(
                        rs.getBigDecimal("quantity") == null
                            ? null
                            : InventoryRules.number(rs.getBigDecimal("quantity")))
                    .trackStock(rs.getBoolean("track_stock")),
            recipe);
    Recipe row = rows.get(0);
    return InventoryCalculator.calculate(
        recipe,
        row.getVersion(),
        row.getYieldServings(),
        servings,
        ingredients,
        stock,
        today,
        checkedAt);
  }
}
