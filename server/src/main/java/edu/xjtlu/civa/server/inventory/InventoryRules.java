package edu.xjtlu.civa.server.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Internal validation shared by inventory writes and read-only calculations. */
final class InventoryRules {
  private static final BigDecimal MAX = new BigDecimal("999999999999999.999");

  private InventoryRules() {}

  static BigDecimal quantity(String value) {
    if (value == null || !value.matches("(0|[1-9][0-9]{0,14})(\\.[0-9]{1,3})?")) {
      throw new IllegalArgumentException("Invalid quantity");
    }
    return new BigDecimal(value);
  }

  static BigDecimal positive(String value) {
    BigDecimal number = quantity(value);
    if (number.signum() <= 0) throw new IllegalArgumentException("Quantity must be positive");
    return number;
  }

  static String number(BigDecimal value) {
    if (value.signum() < 0 || value.compareTo(MAX) > 0)
      throw new IllegalArgumentException("Quantity out of range");
    return value.stripTrailingZeros().toPlainString();
  }

  static BigDecimal required(BigDecimal quantity, BigDecimal servings, BigDecimal yield) {
    BigDecimal result = quantity.multiply(servings).divide(yield, 3, RoundingMode.CEILING);
    number(result);
    return result;
  }

  static LocalDate date(String value) {
    if (value == null) return null;
    if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
      throw new IllegalArgumentException("Invalid date");
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException error) {
      throw new IllegalArgumentException("Invalid date");
    }
  }

  static String location(String value) {
    if (value == null || value.codePointCount(0, value.length()) > 120)
      throw new IllegalArgumentException("Invalid location");
    return value;
  }

  static void page(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new IllegalArgumentException("Invalid pagination");
  }

  static void version(long actual, Long expected) {
    if (expected == null || expected < 0) throw new IllegalArgumentException("Invalid version");
    if (actual != expected) throw new InventoryFailure("VERSION_CONFLICT");
  }
}
