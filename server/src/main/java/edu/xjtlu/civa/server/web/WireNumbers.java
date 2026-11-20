package edu.xjtlu.civa.server.web;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Wire formatting shared by every business package; quantities are decimal strings, never doubles. */
public final class WireNumbers {
  private static final BigDecimal MAX = new BigDecimal("999999999999999.999");

  private WireNumbers() {}

  public static String number(BigDecimal value) {
    BigDecimal rounded = value.setScale(3, RoundingMode.HALF_UP);
    if (rounded.signum() < 0 || rounded.compareTo(MAX) > 0)
      throw new IllegalArgumentException("Quantity overflow");
    return rounded.stripTrailingZeros().toPlainString();
  }
}
