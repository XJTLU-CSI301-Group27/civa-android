package edu.xjtlu.civa.feature.ac.inventory;

import static org.junit.Assert.*;

import edu.xjtlu.civa.api.model.Ingredient;
import edu.xjtlu.civa.api.model.Unit;
import java.util.*;
import org.junit.Test;

public class IngredientChoicesTest {
  private Ingredient food(String name, Unit unit, String... aliases) {
    return new Ingredient()
        .id(UUID.randomUUID())
        .name(name)
        .unit(unit)
        .aliases(Arrays.asList(aliases));
  }

  @Test
  public void appleCountAliasesDoNotRelabelHistoricalGramStock() {
    Ingredient grams = food("苹果（按克）", Unit.G);
    Ingredient items = food("苹果", Unit.COUNT, "apple", "apples");
    IngredientChoices choices =
        new IngredientChoices(Arrays.asList(grams, items), Collections.singleton(grams.getId()));
    assertSame(items, choices.exact("apple"));
    assertSame(items, choices.exact("apples"));
    assertSame(items, choices.exact("苹果"));
    assertSame(grams, choices.exact("苹果（按克）"));
    assertEquals(Unit.G, grams.getUnit());
  }

  @Test
  public void chineseNameAndAliasFindOneCanonicalChoice() {
    Ingredient beef = food("牛肉", Unit.G, "beef");
    Ingredient tomato = food("西红柿", Unit.G, "番茄");
    IngredientChoices choices =
        new IngredientChoices(Arrays.asList(beef, tomato), Collections.emptySet());
    assertSame(beef, choices.exact("牛肉"));
    assertSame(beef, choices.search("牛肉").get(0));
    assertSame(beef, choices.exact("ＢＥＥＦ"));
    assertSame(tomato, choices.exact("番茄"));
  }

  @Test
  public void repeatedNamesUseExistingHouseholdIdWithoutShowingTechnicalAliases() {
    Ingredient duplicate = food("牛肉", Unit.G, "c_ui_1234", "牛肉");
    Ingredient existing = food("牛肉", Unit.G, "beef");
    IngredientChoices choices =
        new IngredientChoices(
            Arrays.asList(duplicate, existing), Collections.singleton(existing.getId()));
    assertEquals(1, choices.search("牛肉").size());
    assertSame(existing, choices.exact("牛肉"));
    assertEquals("牛肉 · g", IngredientChoices.label(existing));
    assertTrue(choices.search("c_ui_").isEmpty());
  }

  @Test
  public void englishSearchNormalizesSpacingAndRanksPrefixBeforeInfix() {
    Ingredient prefix = food("Beef", Unit.G, "beef");
    Ingredient infix = food("A smoked beef", Unit.G, "smoked beef");
    Ingredient spaced = food("Fresh pear", Unit.COUNT, "fresh pear");
    IngredientChoices choices =
        new IngredientChoices(Arrays.asList(infix, spaced, prefix), Collections.emptySet());
    assertTrue(choices.search("e").isEmpty());
    assertSame(prefix, choices.search("be").get(0));
    assertSame(spaced, choices.exact("  ＦＲＥＳＨ   PEAR  "));
    assertEquals("Fresh pear · pcs", IngredientChoices.label(spaced));
  }

  @Test
  public void distinctUnitsRemainDistinctAndNeedExplicitSelection() {
    IngredientChoices choices =
        new IngredientChoices(
            Arrays.asList(food("牛奶", Unit.ML), food("牛奶", Unit.COUNT)), Collections.emptySet());
    assertEquals(2, choices.search("牛奶").size());
    assertNull(choices.exact("牛奶"));
    assertEquals(Unit.ML, choices.exact("牛奶", Unit.ML).getUnit());
    assertTrue(choices.search("").isEmpty());
    assertNull(choices.exact("不存在的食材"));
  }
}
