package edu.xjtlu.civa.feature.bd.planning;

import edu.xjtlu.civa.api.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Pure, seedable rules. It never calls a network API or edits stored plans. */
public final class RecommendationEngine {
  public List<Recipe> candidates(List<Recipe> recipes, Preference preference, int maxMinutes) {
    List<Recipe> result = new ArrayList<>();
    Set<UUID> excluded =
        preference == null
            ? Collections.emptySet()
            : new HashSet<>(preference.getExcludedIngredientIds());
    Set<String> likes =
        preference == null ? Collections.emptySet() : new HashSet<>(preference.getLikedTags());
    for (Recipe recipe : recipes) {
      if (Boolean.TRUE.equals(recipe.getArchived())
          || recipe.getDurationMinutes() > maxMinutes
          || recipe.getIngredients() == null
          || recipe.getIngredients().isEmpty()) continue;
      if (recipe.getIngredients().stream()
          .anyMatch(i -> i.getIngredientId() == null || excluded.contains(i.getIngredientId())))
        continue;
      if (!likes.isEmpty() && Collections.disjoint(likes, recipe.getTags())) continue;
      result.add(recipe);
    }
    return result;
  }

  public List<Recipe> rank(
      List<Recipe> candidates,
      Set<UUID> wishlist,
      Map<UUID, RecipeMatch> matches,
      List<PlanSlot> history) {
    Map<UUID, Integer> frequency = new HashMap<>();
    for (PlanSlot slot : history)
      for (PlanItem item : slot.getItems()) frequency.merge(item.getRecipeId(), 1, Integer::sum);
    List<Recipe> result = new ArrayList<>(candidates);
    result.sort(
        Comparator.<Recipe, Boolean>comparing(r -> !wishlist.contains(r.getId()))
            .thenComparing(r -> coverage(matches.get(r.getId())), Comparator.reverseOrder())
            .thenComparing(
                r -> expiry(matches.get(r.getId())),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(r -> frequency.getOrDefault(r.getId(), 0))
            .thenComparing(r -> r.getId().toString()));
    return result;
  }

  public List<PlanSlotWrite> preview(
      LocalDate start,
      int days,
      List<PlanMealType> meals,
      List<PlanSlot> saved,
      List<Recipe> choices,
      String servings,
      boolean random,
      long seed) {
    if (days < 1 || days > 14 || meals.isEmpty() || new HashSet<>(meals).size() != meals.size())
      throw new IllegalArgumentException("Choose 1–14 days and distinct meal slots");
    if (servings == null
        || !servings.matches("(0|[1-9][0-9]{0,14})(\\.[0-9]{1,3})?")
        || new BigDecimal(servings).signum() <= 0)
      throw new IllegalArgumentException("Invalid servings");
    Map<String, PlanSlot> existing = new HashMap<>();
    for (PlanSlot slot : saved) existing.put(slot.getDate() + slot.getMealType(), slot);
    List<Recipe> available = new ArrayList<>(choices);
    if (random) Collections.shuffle(available, new Random(seed));
    List<PlanSlotWrite> result = new ArrayList<>();
    int next = 0;
    for (int day = 0; day < days; day++)
      for (PlanMealType meal : meals) {
        String date = start.plusDays(day).toString();
        PlanSlot old = existing.get(date + meal);
        if (old != null && (Boolean.TRUE.equals(old.getLocked()) || !old.getItems().isEmpty()))
          continue;
        if (available.isEmpty()) continue;
        Recipe recipe = available.get(next++ % available.size());
        PlanItem item = new PlanItem();
        item.setRecipeId(recipe.getId());
        item.setServings(servings);
        PlanSlotWrite write = new PlanSlotWrite();
        write.setDate(date);
        write.setMealType(meal);
        write.setExpectedVersion(old == null ? 0L : old.getVersion());
        write.setItems(Collections.singletonList(item));
        result.add(write);
      }
    return result;
  }

  public String reason(Recipe recipe, Set<UUID> wishlist, RecipeMatch match) {
    List<String> parts = new ArrayList<>();
    if (wishlist.contains(recipe.getId())) parts.add("在我的想吃清单");
    if (match != null) {
      parts.add(
          "食材覆盖 "
              + new BigDecimal(match.getCoverage())
                  .multiply(BigDecimal.valueOf(100))
                  .stripTrailingZeros()
                  .toPlainString()
              + "%");
      if (match.getEarliestExpiry() != null) parts.add("可利用临期库存 " + match.getEarliestExpiry());
    }
    if (parts.isEmpty()) parts.add("符合我的筛选条件");
    return String.join(" · ", parts);
  }

  private static BigDecimal coverage(RecipeMatch match) {
    return match == null ? BigDecimal.ZERO : new BigDecimal(match.getCoverage());
  }

  private static String expiry(RecipeMatch match) {
    return match == null ? null : match.getEarliestExpiry();
  }
}
