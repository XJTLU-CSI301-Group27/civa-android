package edu.xjtlu.civa.feature.ac.inventory;

import static org.junit.Assert.*;

import edu.xjtlu.civa.api.model.InventoryBatch;
import edu.xjtlu.civa.api.model.Unit;
import java.time.LocalDate;
import org.junit.Test;

public class InventoryPresentationTest {
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
