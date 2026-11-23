package edu.xjtlu.civa.feature.ac.inventory;

import static org.junit.Assert.*;

import edu.xjtlu.civa.api.model.InventoryBatch;
import edu.xjtlu.civa.api.model.Unit;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.junit.Test;

public class InventoryPresentationTest {
  @Test
  public void commonEnglishFoodsAndPluralsUseTheExpectedCategoryWithoutSubstringMistakes() {
    assertEquals(0, InventoryPresentation.suggestedShelf("cheese"));
    assertEquals(0, InventoryPresentation.suggestedShelf("spring water"));
    assertEquals(4, InventoryPresentation.suggestedShelf("prawns"));
    assertEquals(4, InventoryPresentation.suggestedShelf("salmon"));
    assertEquals(3, InventoryPresentation.suggestedShelf("sausages"));
    assertEquals(1, InventoryPresentation.suggestedShelf("eggplant"));
    assertEquals("🐟", InventoryPresentation.emoji("salmon"));
  }

  @Test
  public void fridgeHidesOnlyEmptyStockAndPreservesUnknown() {
    assertFalse(InventoryPresentation.inFridge(new InventoryBatch().quantity("0")));
    assertFalse(InventoryPresentation.inFridge(new InventoryBatch().quantity("0.000")));
    assertTrue(InventoryPresentation.inFridge(new InventoryBatch().quantity("0.001")));
    assertTrue(InventoryPresentation.inFridge(new InventoryBatch()));
  }

  @Test
  public void depletionOnlyAppliesToSelectedBatchOfTheSameIngredient() {
    UUID food = UUID.randomUUID();
    InventoryBatch current =
        new InventoryBatch().id(UUID.randomUUID()).ingredientId(food).quantity("3");
    InventoryBatch empty =
        new InventoryBatch().id(UUID.randomUUID()).ingredientId(food).quantity("0");
    InventoryBatch other =
        new InventoryBatch().id(UUID.randomUUID()).ingredientId(food).quantity("2");
    InventoryBatch different =
        new InventoryBatch().id(UUID.randomUUID()).ingredientId(UUID.randomUUID()).quantity("10");
    assertFalse(
        InventoryPresentation.otherBatchesRemain(
            current, Arrays.asList(current, empty, different)));
    assertTrue(InventoryPresentation.otherBatchesRemain(current, Arrays.asList(current, other)));
    assertTrue(
        InventoryPresentation.otherBatchesRemain(
            current, Arrays.asList(current, other.quantity(null))));
  }

  @Test
  public void entryUnitsConvertKilogramsWithoutConvertingCountToWeight() {
    assertEquals(Unit.G, InventoryPresentation.inputBaseUnit(4));
    assertEquals(Unit.COUNT, InventoryPresentation.inputBaseUnit(2));
    assertEquals(Unit.ML, InventoryPresentation.inputBaseUnit(3));
    assertEquals("1250", InventoryPresentation.inputQuantity("1.25", 4));
    assertEquals("1", InventoryPresentation.inputQuantity("0.001", 4));
    assertEquals("200", InventoryPresentation.inputQuantity("200", 1));
    assertEquals("3", InventoryPresentation.inputQuantity("3", 2));
    assertThrows(IllegalArgumentException.class, () -> InventoryPresentation.inputQuantity("1", 0));
    assertThrows(IllegalArgumentException.class, () -> InventoryPresentation.inputQuantity("0", 4));
    assertThrows(
        IllegalArgumentException.class,
        () -> InventoryPresentation.inputQuantity("999999999999999", 4));
  }

  @Test
  public void englishFruitNamesUseFruitShelfAndPearIllustration() {
    for (String name :
        new String[] {"pear", "PEARS", "fresh pear", "banana", "grapes", "mango", "strawberries"})
      assertEquals(2, InventoryPresentation.suggestedShelf(name));
    assertEquals("🍐", InventoryPresentation.emoji("pear"));
    assertEquals(1, InventoryPresentation.suggestedShelf("pearl barley"));
  }

  @Test
  public void consumptionUsesExactDecimalsAndCannotOverdraw() {
    assertEquals("350", InventoryPresentation.after("500", "150"));
    assertEquals("0.2", InventoryPresentation.after("0.3", "0.1"));
    assertEquals("0", InventoryPresentation.after("6", "6"));
    for (String amount : new String[] {"0", "-1", "501", "0.0001", "abc"})
      assertThrows(
          IllegalArgumentException.class, () -> InventoryPresentation.after("500", amount));
  }

  @Test
  public void canonicalUnitsDoNotInventBottleCapacity() {
    assertEquals("毫升", InventoryPresentation.unit(Unit.ML, "牛奶"));
    assertEquals("瓶", InventoryPresentation.unit(Unit.COUNT, "牛奶"));
    assertEquals("个", InventoryPresentation.unit(Unit.COUNT, "虾"));
    assertEquals("克", InventoryPresentation.unit(Unit.G, "牛肉"));
    assertEquals("克", InventoryPresentation.unit(Unit.G, "beef"));
    assertEquals("个", InventoryPresentation.unit(Unit.COUNT, "egg"));
    assertEquals("瓶", InventoryPresentation.unit(Unit.COUNT, "almond milk"));
  }

  @Test
  public void addRequiresPositivePlainDecimalButCorrectionMayReachZero() {
    assertEquals("0.125", InventoryPresentation.positiveQuantity("0.125"));
    assertEquals("0", InventoryPresentation.number(InventoryPresentation.quantity("0")));
    for (String value : new String[] {"0", "-1", "1e2", ".5", "0.0001"})
      assertThrows(
          IllegalArgumentException.class, () -> InventoryPresentation.positiveQuantity(value));
  }

  @Test
  public void defaultShelvesFollowDemoWithoutChangingCanonicalUnits() {
    assertEquals(0, InventoryPresentation.suggestedShelf("牛奶"));
    assertEquals(0, InventoryPresentation.suggestedShelf("鸡蛋"));
    assertEquals(1, InventoryPresentation.suggestedShelf("西红柿"));
    assertEquals(2, InventoryPresentation.suggestedShelf("苹果"));
    assertEquals(3, InventoryPresentation.suggestedShelf("牛肉"));
    assertEquals(4, InventoryPresentation.suggestedShelf("虾"));
    assertEquals(3, InventoryPresentation.suggestedShelf("Smoked Beef"));
    assertEquals(0, InventoryPresentation.suggestedShelf("egg"));
    assertEquals(4, InventoryPresentation.suggestedShelf("shrimp"));
  }

  @Test
  public void dateAndLocationDoNotInventUnknownValues() {
    assertNull(InventoryPresentation.date(""));
    assertEquals("2024-02-29", InventoryPresentation.date("2024-02-29"));
    assertThrows(IllegalArgumentException.class, () -> InventoryPresentation.date("2025-02-29"));
    assertEquals(-1, InventoryPresentation.shelf("厨房橱柜"));
    assertEquals(4, InventoryPresentation.shelf("冷冻 · 海鲜抽屉"));
  }

  @Test
  public void expiryBoundariesAndEmptyStockAreVisible() {
    LocalDate today = LocalDate.of(2026, 11, 22);
    InventoryBatch batch = new InventoryBatch().quantity("1").expiresOn("2026-11-21");
    assertTrue(InventoryPresentation.expired(batch, today));
    assertEquals("已过期", InventoryPresentation.expiry(batch, today));
    assertEquals("临期 · 3天后", InventoryPresentation.expiry(batch.expiresOn("2026-11-25"), today));
    assertEquals(
        "2026-11-26 到期", InventoryPresentation.expiry(batch.expiresOn("2026-11-26"), today));
    assertEquals("到期日期未知", InventoryPresentation.expiry(batch.expiresOn(null), today));
    assertEquals("已用完", InventoryPresentation.expiry(batch.quantity("0"), today));
  }
}
