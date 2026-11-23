package edu.xjtlu.civa.server.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IngredientNamesTest {

  @Test
  void normalizesCaseWidthAndWhitespace() {
    assertEquals("tomato", IngredientNames.normalize("  Tomato  "));
    assertEquals("tomato", IngredientNames.normalize("ＴＯＭＡＴＯ"));
    assertEquals("green onion", IngredientNames.normalize("green    onion"));
    assertEquals("西红柿", IngredientNames.normalize("  西红柿  "));
    assertEquals("番茄", IngredientNames.normalize("  番茄  "));
  }

  @Test
  void lengthUsesUnicodeCodePointsRatherThanUtf16Characters() {
    assertEquals("a".repeat(120), IngredientNames.normalize("a".repeat(120)));
    assertEquals("😀".repeat(120), IngredientNames.normalize("😀".repeat(120)));
    assertThrows(IllegalArgumentException.class, () -> IngredientNames.normalize("😀".repeat(121)));
  }

  @Test
  void rejectsMissingBlankAndOverlongNames() {
    assertThrows(IllegalArgumentException.class, () -> IngredientNames.normalize(null));

    assertThrows(IllegalArgumentException.class, () -> IngredientNames.normalize("   "));

    assertThrows(IllegalArgumentException.class, () -> IngredientNames.normalize("a".repeat(121)));
  }
}
