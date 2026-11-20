package edu.xjtlu.civa.server.planning;

import edu.xjtlu.civa.server.web.ApiFailure;
import edu.xjtlu.civa.server.web.WireNumbers;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Shared BD validation; wire quantities are decimal strings, never floating point. */
public final class BdRules {
  private BdRules() {}

  public static LocalDate date(String value) {
    try {
      LocalDate date = LocalDate.parse(value);
      if (!date.toString().equals(value)) throw new IllegalArgumentException("Invalid date");
      return date;
    } catch (DateTimeParseException | NullPointerException ex) {
      throw new IllegalArgumentException("Invalid date");
    }
  }

  public static void range(String from, String to, int maximumDays) {
    long days = ChronoUnit.DAYS.between(date(from), date(to)) + 1;
    if (days < 1 || days > maximumDays) throw new IllegalArgumentException("Invalid date range");
  }

  public static BigDecimal decimal(String value, boolean positive) {
    if (value == null || !value.matches("(0|[1-9][0-9]{0,14})(\\.[0-9]{1,3})?"))
      throw new IllegalArgumentException("Invalid quantity");
    BigDecimal result = new BigDecimal(value);
    if (positive && result.signum() <= 0)
      throw new IllegalArgumentException("Quantity must be positive");
    return result;
  }

  public static String number(BigDecimal value) {
    return WireNumbers.number(value);
  }

  public static String calories(String basis, String amount, String kcal) {
    BigDecimal quantity = decimal(amount, true);
    if ("UNKNOWN".equals(basis)) {
      if (kcal != null) throw new IllegalArgumentException("Unknown calories must be null");
      return null;
    }
    BigDecimal value = decimal(kcal, false);
    return switch (basis) {
      case "SERVING" -> number(quantity.multiply(value));
      case "GRAM" -> number(quantity.multiply(value).movePointLeft(2));
      case "MANUAL" -> {
        if (quantity.compareTo(BigDecimal.ONE) != 0)
          throw new IllegalArgumentException("Manual amount must be 1");
        yield number(value);
      }
      default -> throw new IllegalArgumentException("Invalid calorie basis");
    };
  }

  public static final class Failure extends ApiFailure {
    public Failure(String code) {
      super(code);
    }
  }
}
