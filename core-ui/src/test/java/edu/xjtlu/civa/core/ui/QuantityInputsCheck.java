package edu.xjtlu.civa.core.ui;

import java.math.BigDecimal;

public final class QuantityInputsCheck {
    public static void main(String[] args) {
        check("1.250", "KG", "1250", "G");
        check("0.001", "L", "1", "ML");
        check("0.5", "COUNT", "0.5", "COUNT");
        check("0", "G", "0", "G");
        reject("0.0001", "G");
        reject("1000000000000", "KG");
        reject("-1", "G");
        reject("1", "OZ");
    }

    private static void check(String value, String inputUnit, String amount, String unit) {
        QuantityInputs.Quantity result = QuantityInputs.toBase(new BigDecimal(value), inputUnit);
        if (!amount.equals(result.amount.toPlainString()) || !unit.equals(result.unit)) {
            throw new AssertionError(value + " " + inputUnit);
        }
    }

    private static void reject(String value, String inputUnit) {
        try {
            QuantityInputs.toBase(new BigDecimal(value), inputUnit);
            throw new AssertionError("Expected rejection: " + value + " " + inputUnit);
        } catch (IllegalArgumentException expected) {
            // Expected invalid input.
        }
    }
}
