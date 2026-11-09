package edu.xjtlu.civa.core.ui;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

public final class QuantityInputs {
    private static final Pattern WIRE_NUMBER = Pattern.compile("^(0|[1-9][0-9]{0,14})(\\.[0-9]{1,3})?$");

    private QuantityInputs() {}

    public static Quantity toBase(BigDecimal value, String inputUnit) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(inputUnit, "inputUnit");
        if (!WIRE_NUMBER.matcher(value.toPlainString()).matches()) {
            throw new IllegalArgumentException("Invalid quantity");
        }
        BigDecimal amount;
        String unit;
        switch (inputUnit) {
            case "KG": amount = value.movePointRight(3); unit = "G"; break;
            case "L": amount = value.movePointRight(3); unit = "ML"; break;
            case "G": case "ML": case "COUNT": amount = value; unit = inputUnit; break;
            default: throw new IllegalArgumentException("Unsupported unit");
        }
        amount = amount.stripTrailingZeros();
        if (!WIRE_NUMBER.matcher(amount.toPlainString()).matches()) {
            throw new IllegalArgumentException("Converted quantity exceeds wire limits");
        }
        return new Quantity(amount, unit);
    }

    public static final class Quantity {
        public final BigDecimal amount;
        public final String unit;

        private Quantity(BigDecimal amount, String unit) {
            this.amount = amount;
            this.unit = unit;
        }
    }
}
