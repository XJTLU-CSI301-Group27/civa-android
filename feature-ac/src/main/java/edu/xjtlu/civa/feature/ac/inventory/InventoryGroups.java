package edu.xjtlu.civa.feature.ac.inventory;

import edu.xjtlu.civa.api.model.InventoryBatch;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Read-only UI projection over existing batches; never combines their stored quantities or IDs. */
final class InventoryGroups {
  private InventoryGroups() {}

  static List<Group> from(Collection<InventoryBatch> batches) {
    Map<String, List<InventoryBatch>> grouped = new LinkedHashMap<>();
    for (InventoryBatch batch : batches) {
      if (!InventoryPresentation.inFridge(batch)) continue;
      String key = batch.getIngredientId() + "|" + batch.getUnit();
      grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(batch);
    }
    List<Group> result = new ArrayList<>();
    for (List<InventoryBatch> values : grouped.values()) result.add(new Group(values));
    return result;
  }

  static final class Group {
    final List<InventoryBatch> batches;

    private Group(List<InventoryBatch> values) {
      List<InventoryBatch> sorted = new ArrayList<>(values);
      sorted.sort(
          Comparator.comparing(
                  InventoryBatch::getExpiresOn, Comparator.nullsLast(Comparator.naturalOrder()))
              .thenComparing(
                  InventoryBatch::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
              .thenComparing(b -> b.getId().toString()));
      batches = Collections.unmodifiableList(sorted);
    }

    InventoryBatch first() {
      return batches.get(0);
    }

    boolean contains(String batchId) {
      for (InventoryBatch batch : batches)
        if (batch.getId().toString().equals(batchId)) return true;
      return false;
    }

    int shelf() {
      // One ingredient card across locations; use the first known shelf in fridge order.
      // Every actual location remains visible in its batch row.
      int firstShelf = Integer.MAX_VALUE;
      for (InventoryBatch batch : batches) {
        int shelf = InventoryPresentation.shelf(batch.getLocation());
        if (shelf >= 0) firstShelf = Math.min(firstShelf, shelf);
      }
      return firstShelf == Integer.MAX_VALUE ? -1 : firstShelf;
    }

    String quantity() {
      return sum(null, false);
    }

    String usableQuantity(LocalDate today) {
      return today == null ? null : sum(today, true);
    }

    private String sum(LocalDate today, boolean usableOnly) {
      BigDecimal total = BigDecimal.ZERO;
      for (InventoryBatch batch : batches) {
        if (usableOnly && InventoryPresentation.expired(batch, today)) continue;
        if (batch.getQuantity() == null) return null;
        total = total.add(new BigDecimal(batch.getQuantity()));
      }
      return InventoryPresentation.number(total);
    }
  }
}
