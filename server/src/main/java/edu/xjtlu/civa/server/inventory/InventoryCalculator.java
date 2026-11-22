package edu.xjtlu.civa.server.inventory;

import edu.xjtlu.civa.server.api.model.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Pure calculation; never writes or reserves stock. Uses only existing generated models. */
final class InventoryCalculator {
  record Result(
      RecipeMatch match, List<CookingAllocation> allocations, List<MatchLine> shortages) {}

  static Result calculate(
      UUID recipeId,
      long version,
      String yield,
      String servings,
      List<RecipeIngredient> ingredients,
      List<InventoryBatch> stock,
      LocalDate today,
      String checkedAt) {
    BigDecimal yieldValue = InventoryRules.positive(yield),
        servingsValue = InventoryRules.positive(servings);
    List<MatchLine> lines = new ArrayList<>(), shortages = new ArrayList<>();
    List<CookingAllocation> allocations = new ArrayList<>();
    LocalDate earliest = null;
    int enough = 0;
    for (RecipeIngredient ingredient : ingredients) {
      if (!Boolean.TRUE.equals(ingredient.getTrackStock())) continue;
      List<InventoryBatch> available =
          stock.stream()
              .filter(
                  b ->
                      b.getIngredientId().equals(ingredient.getIngredientId())
                          && b.getUnit() == ingredient.getUnit())
              .filter(b -> InventoryRules.quantity(b.getQuantity()).signum() > 0)
              .filter(
                  b ->
                      b.getExpiresOn() == null
                          || !InventoryRules.date(b.getExpiresOn()).isBefore(today))
              .sorted(
                  Comparator.comparing(
                          InventoryBatch::getExpiresOn,
                          Comparator.nullsLast(Comparator.naturalOrder()))
                      .thenComparing(InventoryBatch::getCreatedAt)
                      .thenComparing(b -> b.getId().toString()))
              .toList();
      BigDecimal total = BigDecimal.ZERO;
      for (InventoryBatch batch : available) {
        total = total.add(InventoryRules.quantity(batch.getQuantity()));
        if (batch.getExpiresOn() != null) {
          LocalDate date = InventoryRules.date(batch.getExpiresOn());
          if (earliest == null || date.isBefore(earliest)) earliest = date;
        }
      }
      InventoryRules.number(total);
      MatchLine line =
          new MatchLine()
              .ingredientId(ingredient.getIngredientId())
              .unit(ingredient.getUnit())
              .available(InventoryRules.number(total));
      if (ingredient.getQuantity() == null) {
        line.required(null).status(MatchStatus.UNKNOWN);
        shortages.add(line);
      } else {
        BigDecimal required =
            InventoryRules.required(
                InventoryRules.quantity(ingredient.getQuantity()), servingsValue, yieldValue);
        line.required(InventoryRules.number(required));
        if (total.compareTo(required) >= 0) {
          line.status(MatchStatus.ENOUGH);
          enough++;
        } else {
          line.status(MatchStatus.MISSING);
          shortages.add(line);
        }
        BigDecimal remaining = required;
        for (InventoryBatch batch : available) {
          if (remaining.signum() == 0) break;
          BigDecimal take = remaining.min(InventoryRules.quantity(batch.getQuantity()));
          allocations.add(
              new CookingAllocation()
                  .batchId(batch.getId())
                  .expectedVersion(batch.getVersion())
                  .ingredientId(ingredient.getIngredientId())
                  .quantity(InventoryRules.number(take))
                  .unit(ingredient.getUnit()));
          remaining = remaining.subtract(take);
        }
      }
      lines.add(line);
    }
    String coverage =
        lines.isEmpty()
            ? "1"
            : InventoryRules.number(
                BigDecimal.valueOf(enough)
                    .divide(BigDecimal.valueOf(lines.size()), 3, RoundingMode.HALF_UP));
    RecipeMatch match =
        new RecipeMatch()
            .recipeId(recipeId)
            .recipeVersion(version)
            .servings(InventoryRules.number(servingsValue))
            .lines(lines)
            .coverage(coverage)
            .earliestExpiry(earliest == null ? null : earliest.toString())
            .checkedAt(checkedAt);
    return new Result(match, List.copyOf(allocations), List.copyOf(shortages));
  }
}
