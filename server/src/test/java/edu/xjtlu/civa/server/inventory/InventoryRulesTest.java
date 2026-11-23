package edu.xjtlu.civa.server.inventory;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InventoryRulesTest {
  @Test
  void quantitiesRejectNegativeExcessPrecisionAndScientificNotation() {
    for (String bad : new String[] {null, "-1", "0.0001", "1e3", "01", "1000000000000000", "NaN"})
      assertThrows(IllegalArgumentException.class, () -> InventoryRules.quantity(bad));
    assertEquals("0", InventoryRules.number(InventoryRules.quantity("0.000")));
    assertEquals("1.2", InventoryRules.number(InventoryRules.quantity("1.200")));
    assertThrows(IllegalArgumentException.class, () -> InventoryRules.positive("0"));
    assertThrows(
        IllegalArgumentException.class,
        () -> InventoryRules.number(new BigDecimal("1000000000000000")));
  }

  @Test
  void scaledRequirementsRoundUpOnlyAtTheEnd() {
    assertEquals(
        new BigDecimal("0.334"),
        InventoryRules.required(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("3")));
    assertEquals(
        new BigDecimal("200.000"),
        InventoryRules.required(new BigDecimal("100"), new BigDecimal("4"), new BigDecimal("2")));
  }

  @Test
  void datesPaginationAndVersionsAreValidated() {
    assertNull(InventoryRules.date(null));
    assertEquals("2024-02-29", InventoryRules.date("2024-02-29").toString());
    for (String date : new String[] {"2025-02-29", "2026-2-01", "2026-13-01", ""})
      assertThrows(IllegalArgumentException.class, () -> InventoryRules.date(date));
    assertThrows(IllegalArgumentException.class, () -> InventoryRules.page(-1, 20));
    assertThrows(IllegalArgumentException.class, () -> InventoryRules.page(0, 101));
    assertThrows(IllegalArgumentException.class, () -> InventoryRules.version(1, null));
    assertEquals(
        "VERSION_CONFLICT",
        assertThrows(InventoryFailure.class, () -> InventoryRules.version(2, 1L)).code);
    InventoryRules.version(2, 2L);
  }
}
