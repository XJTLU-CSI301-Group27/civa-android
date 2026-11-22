package edu.xjtlu.civa.feature.ac.inventory;

import edu.xjtlu.civa.api.model.Ingredient;
import edu.xjtlu.civa.api.model.InventoryBatch;
import edu.xjtlu.civa.api.model.Unit;
import edu.xjtlu.civa.core.ui.QuantityInputs;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/** Feature-local presentation and input rules; canonical units always come from the API. */
final class InventoryPresentation {
  static final String[] LOCATIONS = {"冷藏 · 蛋奶层", "冷藏 · 蔬菜层", "冷藏 · 水果层", "冷冻 · 肉类抽屉", "冷冻 · 海鲜抽屉"};

  private InventoryPresentation() {}

  static String number(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  static BigDecimal quantity(String raw) {
    if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("请输入数量");
    BigDecimal value;
    try {
      value = new BigDecimal(raw.trim());
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException("数量格式不正确");
    }
    return QuantityInputs.toBase(value, "G").amount;
  }

  static String after(String before, String consumed) {
    BigDecimal amount = quantity(consumed), stock = quantity(before);
    if (amount.signum() <= 0 || amount.compareTo(stock) > 0)
      throw new IllegalArgumentException("消耗量必须大于0，且不能超过剩余数量");
    return number(stock.subtract(amount));
  }

  static String date(String raw) {
    if (raw == null || raw.trim().isEmpty()) return null;
    try {
      String value = raw.trim();
      if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
      return LocalDate.parse(value).toString();
    } catch (RuntimeException error) {
      throw new IllegalArgumentException("日期请填写有效的 YYYY-MM-DD，可留空");
    }
  }

  static int shelf(String location) {
    if (location == null) return -1;
    for (int i = 0; i < LOCATIONS.length; i++) if (LOCATIONS[i].equals(location)) return i;
    if (location.contains("冷冻")) return 3;
    if (location.contains("冷藏")) return 1;
    return -1;
  }

  static String name(InventoryBatch batch, Ingredient ingredient) {
    return ingredient == null
        ? "未载入名称 · " + batch.getIngredientId().toString().substring(0, 8)
        : ingredient.getName();
  }

  static String unit(Unit unit, String name) {
    if (unit == Unit.G) return "克";
    if (unit == Unit.ML) return "毫升";
    if (unit == Unit.COUNT) return name.contains("奶") ? "瓶" : "个";
    return "单位未知";
  }

  static String emoji(String name) {
    if (name.contains("虾")) return "🦐";
    if (name.contains("蛋")) return "🥚";
    if (name.contains("奶")) return "🥛";
    if (name.contains("番茄") || name.contains("西红柿")) return "🍅";
    if (name.contains("西兰花")) return "🥦";
    if (name.contains("苹果")) return "🍎";
    if (name.contains("橙") || name.contains("橘")) return "🍊";
    if (name.contains("肉")) return "🥩";
    if (name.contains("鱼")) return "🐟";
    if (name.contains("贝")) return "🐚";
    return "🥬";
  }

  static String expiry(InventoryBatch batch, LocalDate today) {
    if (batch.getQuantity() != null && quantity(batch.getQuantity()).signum() == 0) return "已用完";
    if (batch.getExpiresOn() == null) return "到期日期未知";
    LocalDate expiry = LocalDate.parse(batch.getExpiresOn());
    long days = java.time.temporal.ChronoUnit.DAYS.between(today, expiry);
    if (days < 0) return "已过期";
    if (days == 0) return "今天到期";
    if (days <= 3) return String.format(Locale.ROOT, "临期 · %d天后", days);
    return expiry.toString() + " 到期";
  }

  static boolean expired(InventoryBatch batch, LocalDate today) {
    return batch.getExpiresOn() != null && LocalDate.parse(batch.getExpiresOn()).isBefore(today);
  }
}
