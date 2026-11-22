package edu.xjtlu.civa.server.inventory;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared canonical-name normalization for recipe and inventory ingredients. */
public final class IngredientNames {
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private IngredientNames() {}

  public static String normalize(String input) {
    if (input == null) {
      throw new IllegalArgumentException("Ingredient name is required");
    }

    String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC);
    normalized = WHITESPACE.matcher(normalized.trim()).replaceAll(" ");
    normalized = normalized.toLowerCase(Locale.ROOT);

    if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 120) {
      throw new IllegalArgumentException("Invalid ingredient name");
    }

    return normalized;
  }
}
