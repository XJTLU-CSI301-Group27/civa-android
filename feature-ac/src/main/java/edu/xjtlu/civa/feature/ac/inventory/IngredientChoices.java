package edu.xjtlu.civa.feature.ac.inventory;

import edu.xjtlu.civa.api.model.Ingredient;
import edu.xjtlu.civa.api.model.Unit;
import java.text.Normalizer;
import java.util.*;

/** Display/search choices reuse server IDs; grouping never changes saved stock or recipe IDs. */
final class IngredientChoices {
  private final Map<String, Ingredient> choices = new LinkedHashMap<>();
  private final Map<String, Set<String>> terms = new LinkedHashMap<>();

  IngredientChoices(Collection<Ingredient> ingredients, Set<UUID> usedAtHome) {
    for (Ingredient ingredient : ingredients) {
      String key = key(ingredient);
      Ingredient previous = choices.get(key);
      if (previous == null
          || (usedAtHome.contains(ingredient.getId()) && !usedAtHome.contains(previous.getId())))
        choices.put(key, ingredient);
      Set<String> words = terms.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
      words.add(normalize(ingredient.getName()));
      if (ingredient.getAliases() != null)
        for (String alias : ingredient.getAliases())
          if (alias != null && !alias.matches("(?i)c_ui_[0-9a-f]+")) words.add(normalize(alias));
    }
  }

  List<Ingredient> search(String query) {
    String text = normalize(query);
    if (text.codePointCount(0, text.length()) < 2) return Collections.emptyList();
    List<Ingredient> result = new ArrayList<>();
    for (var entry : choices.entrySet())
      if (terms.get(entry.getKey()).stream().anyMatch(term -> term.contains(text)))
        result.add(entry.getValue());
    result.sort(
        Comparator.comparingInt(
                (Ingredient value) ->
                    terms.get(key(value)).stream().anyMatch(term -> term.startsWith(text)) ? 0 : 1)
            .thenComparing(value -> normalize(value.getName())));
    return result;
  }

  Ingredient exact(String query) {
    return exact(query, null);
  }

  Ingredient exact(String query, Unit unit) {
    String text = normalize(query);
    Ingredient result = null;
    for (var entry : choices.entrySet())
      if ((unit == null || entry.getValue().getUnit() == unit)
          && terms.get(entry.getKey()).contains(text)) {
        if (result != null) return null; // Same name with different units needs an explicit choice.
        result = entry.getValue();
      }
    return result;
  }

  static String label(Ingredient ingredient) {
    return ingredient.getName()
        + " · "
        + (ingredient.getUnit() == Unit.G ? "g" : ingredient.getUnit() == Unit.ML ? "ml" : "pcs");
  }

  static String normalize(String text) {
    return text == null
        ? ""
        : Normalizer.normalize(text, Normalizer.Form.NFKC)
            .trim()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
  }

  private static String key(Ingredient ingredient) {
    return normalize(ingredient.getName()) + "|" + ingredient.getUnit();
  }
}
