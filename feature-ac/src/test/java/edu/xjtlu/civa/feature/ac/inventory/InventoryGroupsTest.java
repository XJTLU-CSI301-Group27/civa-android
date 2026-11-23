package edu.xjtlu.civa.feature.ac.inventory;

import static org.junit.Assert.*;

import edu.xjtlu.civa.api.model.InventoryBatch;
import edu.xjtlu.civa.api.model.Unit;
import java.time.LocalDate;
import java.util.*;
import org.junit.Test;

public class InventoryGroupsTest {
  private InventoryBatch batch(
      UUID ingredient, Unit unit, String amount, String expiry, String location) {
    return new InventoryBatch()
        .id(UUID.randomUUID())
        .ingredientId(ingredient)
        .unit(unit)
        .quantity(amount)
        .expiresOn(expiry)
        .location(location)
        .createdAt("2026-11-22T01:00:00Z");
  }

  @Test
  public void tenPlusTwoUsesOneGroupAndPreservesBothBatchesAndDates() {
    UUID egg = UUID.randomUUID();
    InventoryBatch old = batch(egg, Unit.COUNT, "10", "2026-11-24", "冷藏 · 蛋奶层");
    InventoryBatch fresh =
        batch(egg, Unit.COUNT, "2", "2026-12-01", "冷藏 · 蛋奶层").createdAt("2026-11-22T02:00:00Z");
    InventoryBatch empty = batch(egg, Unit.COUNT, "0", "2026-11-23", "冷藏 · 蛋奶层");
    var groups = InventoryGroups.from(Arrays.asList(fresh, empty, old));
    assertEquals(1, groups.size());
    assertEquals("12", groups.get(0).quantity());
    assertEquals(Arrays.asList(old, fresh), groups.get(0).batches);
    assertEquals("10", old.getQuantity());
    assertEquals("2", fresh.getQuantity());
    assertEquals("2026-12-01", fresh.getExpiresOn());
    assertTrue(groups.get(0).contains(fresh.getId().toString()));
    assertFalse(groups.get(0).contains(empty.getId().toString()));
  }

  @Test
  public void differentStandardIdsAndDimensionsNeverSumTogether() {
    UUID id = UUID.randomUUID();
    var groups =
        InventoryGroups.from(
            Arrays.asList(
                batch(id, Unit.G, "10", null, "冷藏 · 蔬菜层"),
                batch(id, Unit.COUNT, "2", null, "冷藏 · 蔬菜层"),
                batch(UUID.randomUUID(), Unit.G, "3", null, "冷藏 · 蔬菜层")));
    assertEquals(3, groups.size());
    assertEquals("10", groups.get(0).quantity());
    assertEquals("2", groups.get(1).quantity());
    assertEquals("3", groups.get(2).quantity());
  }

  @Test
  public void decimalTotalsAreExactAndUnknownNeverBecomesZero() {
    UUID id = UUID.randomUUID();
    InventoryBatch a = batch(id, Unit.G, "0.1", null, "冷冻 · 肉类抽屉");
    InventoryBatch b = batch(id, Unit.G, "0.2", null, "冷冻 · 肉类抽屉");
    assertEquals("0.3", InventoryGroups.from(Arrays.asList(a, b)).get(0).quantity());
    b.quantity(null);
    assertNull(InventoryGroups.from(Arrays.asList(a, b)).get(0).quantity());
  }

  @Test
  public void expiredStockIsExcludedFromAvailableButStillHasItsOwnBatch() {
    UUID id = UUID.randomUUID();
    InventoryBatch expired = batch(id, Unit.G, "250", "2026-11-21", "冷冻 · 肉类抽屉");
    InventoryBatch usable = batch(id, Unit.G, "500", "2026-12-01", "冷冻 · 肉类抽屉");
    var group = InventoryGroups.from(Arrays.asList(usable, expired)).get(0);
    assertEquals("750", group.quantity());
    assertEquals("500", group.usableQuantity(LocalDate.of(2026, 11, 22)));
    assertSame(expired, group.first());
    assertNull(group.usableQuantity(null));
  }

  @Test
  public void locationsStayInBatchRowsWithoutDuplicatingTheIngredientCard() {
    UUID id = UUID.randomUUID();
    InventoryBatch cold = batch(id, Unit.COUNT, "2", null, "冷藏 · 蛋奶层");
    InventoryBatch frozen = batch(id, Unit.COUNT, "3", null, "冷冻 · 肉类抽屉");
    var group = InventoryGroups.from(Arrays.asList(frozen, cold)).get(0);
    assertEquals("5", group.quantity());
    assertEquals(0, group.shelf());
    assertEquals("冷冻 · 肉类抽屉", frozen.getLocation());
    assertEquals(2, group.batches.size());
  }
}
