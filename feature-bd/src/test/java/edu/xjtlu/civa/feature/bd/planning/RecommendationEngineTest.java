package edu.xjtlu.civa.feature.bd.planning;

import static org.junit.Assert.*;

import edu.xjtlu.civa.api.model.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.Test;

public class RecommendationEngineTest {
  private final RecommendationEngine rules = new RecommendationEngine();

  @Test
  public void generationPreservesLockedAndNonemptySlotsWithoutMutatingOriginals() {
    LocalDate day = LocalDate.of(2026, 11, 20);
    Recipe recipe = recipe("00000000-0000-0000-0000-000000000001");
    PlanSlot locked = new PlanSlot();
    locked.setDate(day.toString());
    locked.setMealType(PlanMealType.BREAKFAST);
    locked.setLocked(true);
    locked.setVersion(2L);
    locked.setItems(List.of());
    PlanItem item = new PlanItem();
    item.setRecipeId(recipe.getId());
    item.setServings("1");
    PlanSlot filled = new PlanSlot();
    filled.setDate(day.toString());
    filled.setMealType(PlanMealType.LUNCH);
    filled.setLocked(false);
    filled.setVersion(1L);
    filled.setItems(List.of(item));
    var result =
        rules.preview(
            day,
            14,
            List.of(PlanMealType.BREAKFAST, PlanMealType.LUNCH, PlanMealType.DINNER),
            List.of(locked, filled),
            List.of(recipe),
            "2",
            false,
            1);
    assertEquals(40, result.size());
    assertEquals(PlanMealType.DINNER, result.get(0).getMealType());
    assertEquals(Long.valueOf(0), result.get(0).getExpectedVersion());
    assertTrue(locked.getLocked());
    assertEquals("1", filled.getItems().get(0).getServings());
  }

  @Test
  public void restrictionsAreNeverRelaxedAndMissingIngredientsAreNotAssumedSafe() {
    Recipe allowed = recipe("00000000-0000-0000-0000-000000000001"),
        excluded = recipe("00000000-0000-0000-0000-000000000002");
    UUID avoid = excluded.getIngredients().get(0).getIngredientId();
    Preference preference = new Preference();
    preference.setExcludedIngredientIds(new HashSet<>(List.of(avoid)));
    preference.setLikedTags(new HashSet<>(List.of("清淡")));
    assertEquals(List.of(allowed), rules.candidates(List.of(allowed, excluded), preference, 60));
    allowed.setIngredients(List.of());
    assertTrue(rules.candidates(List.of(allowed, excluded), preference, 60).isEmpty());
    assertTrue(
        rules
            .preview(
                LocalDate.of(2026, 11, 20),
                1,
                List.of(PlanMealType.LUNCH),
                List.of(),
                List.of(),
                "1",
                true,
                1)
            .isEmpty());
  }

  @Test
  public void recommendationUsesWishlistThenCoverageAndIsDeterministic() {
    Recipe a = recipe("00000000-0000-0000-0000-000000000001"),
        b = recipe("00000000-0000-0000-0000-000000000002"),
        c = recipe("00000000-0000-0000-0000-000000000003");
    RecipeMatch ma = new RecipeMatch();
    ma.setCoverage("0.1");
    RecipeMatch mb = new RecipeMatch();
    mb.setCoverage("1");
    RecipeMatch mc = new RecipeMatch();
    mc.setCoverage("0.5");
    assertEquals(
        List.of(a, b, c),
        rules.rank(
            List.of(c, b, a),
            Set.of(a.getId()),
            Map.of(a.getId(), ma, b.getId(), mb, c.getId(), mc),
            List.of()));
    var first =
        rules.preview(
            LocalDate.of(2026, 11, 20),
            7,
            List.of(PlanMealType.LUNCH),
            List.of(),
            List.of(a, b, c),
            "1",
            true,
            42);
    var second =
        rules.preview(
            LocalDate.of(2026, 11, 20),
            7,
            List.of(PlanMealType.LUNCH),
            List.of(),
            List.of(a, b, c),
            "1",
            true,
            42);
    assertEquals(first, second);
  }

  @Test
  public void invalidDateSpansAndQuantitiesAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            rules.preview(
                LocalDate.now(),
                15,
                List.of(PlanMealType.LUNCH),
                List.of(),
                List.of(),
                "1",
                false,
                1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            rules.preview(
                LocalDate.now(),
                1,
                List.of(PlanMealType.LUNCH),
                List.of(),
                List.of(),
                "0",
                false,
                1));
  }

  private Recipe recipe(String id) {
    Recipe recipe = new Recipe();
    recipe.setId(UUID.fromString(id));
    recipe.setArchived(false);
    recipe.setName("Dish");
    recipe.setDurationMinutes(20);
    recipe.setTags(new HashSet<>(List.of("清淡")));
    RecipeIngredient ingredient = new RecipeIngredient();
    ingredient.setIngredientId(UUID.randomUUID());
    ingredient.setQuantity("1");
    ingredient.setUnit(Unit.G);
    ingredient.setTrackStock(true);
    recipe.setIngredients(List.of(ingredient));
    return recipe;
  }
}
