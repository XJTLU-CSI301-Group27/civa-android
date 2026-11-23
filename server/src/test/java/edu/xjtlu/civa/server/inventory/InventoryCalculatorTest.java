package edu.xjtlu.civa.server.inventory;

import static org.junit.jupiter.api.Assertions.*;

import edu.xjtlu.civa.server.api.model.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryCalculatorTest {
  private final UUID ingredient = UUID.randomUUID(), recipe = UUID.randomUUID();
  private final LocalDate today = LocalDate.of(2026, 11, 21);
  private final String checkedAt = "2026-11-21T00:00:00.000Z";

  @Test
  void combinesUsableBatchesAndAllocatesEarliestFirstWithoutMutation() {
    var unknown = batch("3", null, "2026-01-01T00:00:00.000Z");
    var later = batch("2", "2026-11-24", "2026-01-02T00:00:00.000Z");
    var first = batch("1", "2026-11-21", "2026-01-03T00:00:00.000Z");
    var expired = batch("100", "2026-11-20", "2026-01-01T00:00:00.000Z");
    var empty = batch("0", "2026-11-21", "2026-01-01T00:00:00.000Z");
    var result =
        calculate(List.of(line("4", true)), List.of(unknown, later, expired, empty, first));
    assertEquals("6", result.match().getLines().get(0).getAvailable());
    assertEquals("1", result.match().getCoverage());
    assertEquals("2026-11-21", result.match().getEarliestExpiry());
    assertTrue(result.shortages().isEmpty());
    assertEquals(
        List.of(first.getId(), later.getId(), unknown.getId()),
        result.allocations().stream().map(CookingAllocation::getBatchId).toList());
    assertEquals(
        List.of("1", "2", "1"),
        result.allocations().stream().map(CookingAllocation::getQuantity).toList());
    assertEquals("3", unknown.getQuantity());
    assertEquals("1", first.getQuantity());
  }

  @Test
  void partialAndMissingAreReportedUsingExistingMissingStatus() {
    var partial = calculate(List.of(line("3", true)), List.of(batch("1", null, checkedAt)));
    assertEquals(MatchStatus.MISSING, partial.shortages().get(0).getStatus());
    assertEquals("1", partial.shortages().get(0).getAvailable());
    assertEquals("3", partial.shortages().get(0).getRequired());
    assertEquals("0", partial.match().getCoverage());
    var missing = calculate(List.of(line("3", true)), List.of());
    assertEquals("0", missing.shortages().get(0).getAvailable());
    assertTrue(missing.allocations().isEmpty());
  }

  @Test
  void ignoredIngredientsUnknownRequirementsAndCoverageAreHandled() {
    var untracked = line("999", false).ingredientId(UUID.randomUUID());
    var unknown = line(null, true).ingredientId(UUID.randomUUID());
    var missing = line("1", true).ingredientId(UUID.randomUUID());
    var result =
        calculate(
            List.of(line("1", true), untracked, unknown, missing),
            List.of(batch("1", null, checkedAt)));
    assertEquals(3, result.match().getLines().size());
    assertEquals("0.333", result.match().getCoverage());
    assertEquals(MatchStatus.UNKNOWN, result.shortages().get(0).getStatus());
    assertNull(result.shortages().get(0).getRequired());
    var none = calculate(List.of(untracked), List.of());
    assertEquals("1", none.match().getCoverage());
    assertNull(none.match().getEarliestExpiry());
  }

  @Test
  void tieBreakUsesCreationThenIdAndUnitMismatchIsNotCounted() {
    var a =
        batch("1", "2026-11-24", checkedAt)
            .id(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    var b =
        batch("1", "2026-11-24", checkedAt)
            .id(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    var old = batch("1", "2026-11-24", "2026-11-20T00:00:00.000Z");
    var wrong = batch("999", null, checkedAt).unit(Unit.ML);
    var result = calculate(List.of(line("3", true)), List.of(b, wrong, a, old));
    assertEquals(
        List.of(old.getId(), a.getId(), b.getId()),
        result.allocations().stream().map(CookingAllocation::getBatchId).toList());
    assertEquals("3", result.match().getLines().get(0).getAvailable());
  }

  private RecipeIngredient line(String quantity, boolean track) {
    return new RecipeIngredient()
        .ingredientId(ingredient)
        .quantity(quantity)
        .unit(Unit.G)
        .trackStock(track);
  }

  private InventoryBatch batch(String quantity, String expiry, String created) {
    return new InventoryBatch()
        .id(UUID.randomUUID())
        .ingredientId(ingredient)
        .unit(Unit.G)
        .quantity(quantity)
        .expiresOn(expiry)
        .createdAt(created)
        .version(1L);
  }

  private InventoryCalculator.Result calculate(
      List<RecipeIngredient> lines, List<InventoryBatch> stock) {
    return InventoryCalculator.calculate(recipe, 1, "1", "1", lines, stock, today, checkedAt);
  }
}
