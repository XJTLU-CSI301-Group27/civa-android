package edu.xjtlu.civa.server.planning;

import edu.xjtlu.civa.server.api.model.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningService {
  private final JdbcTemplate jdbc;
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  public PlanningService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Membership is rechecked on every request, including idempotent replay. */
  public void requireMember(UUID actor, UUID home, boolean lock) {
    if (lock)
      jdbc.query(
          "SELECT id FROM households WHERE id=? FOR UPDATE", (rs, n) -> rs.getObject(1), home);
    List<Boolean> rows =
        jdbc.query(
            "SELECT active FROM memberships WHERE user_id=? AND household_id=? ORDER BY active DESC"
                + " LIMIT 1",
            (rs, n) -> rs.getBoolean(1),
            actor,
            home);
    if (rows.isEmpty()) throw new BdRules.Failure("NOT_FOUND");
    if (!rows.get(0)) throw new BdRules.Failure("HOUSEHOLD_ACCESS_REVOKED");
  }

  @Transactional(readOnly = true)
  public PlanSlotPage list(UUID actor, UUID home, String from, String to, int page, int size) {
    BdRules.range(from, to, 21);
    pagination(page, size);
    requireMember(actor, home, false);
    String filter = " FROM plan_slots WHERE household_id=? AND date BETWEEN ?::date AND ?::date";
    Long count = jdbc.queryForObject("SELECT count(*)" + filter, Long.class, home, from, to);
    List<PlanSlot> items =
        jdbc.query(
            "SELECT *"
                + filter
                + " ORDER BY date,CASE meal_type WHEN 'BREAKFAST' THEN 0 WHEN 'LUNCH' THEN 1 ELSE 2"
                + " END,id LIMIT ? OFFSET ?",
            this::map,
            home,
            from,
            to,
            size,
            (long) page * size);
    return new PlanSlotPage().items(items).page(page).size(size).total(count);
  }

  @Transactional
  public PlanSlot replace(
      UUID actor, UUID home, String date, PlanMealType meal, List<PlanItem> items, long expected) {
    requireMember(actor, home, true);
    return write(home, date, meal, items, expected);
  }

  @Transactional
  public PlanSlot lock(
      UUID actor, UUID home, String date, PlanMealType meal, boolean locked, long expected) {
    requireMember(actor, home, true);
    BdRules.date(date);
    PlanSlot slot = find(home, date, meal);
    long version = slot == null ? 0 : slot.getVersion();
    if (expected != version) throw new BdRules.Failure("VERSION_CONFLICT");
    if (slot == null) {
      jdbc.update(
          "INSERT INTO plan_slots(id,household_id,date,meal_type,locked) VALUES (?,?,?::date,?,?)",
          UUID.randomUUID(),
          home,
          date,
          meal.getValue(),
          locked);
    } else {
      jdbc.update(
          "UPDATE plan_slots SET locked=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE"
              + " id=?",
          locked,
          slot.getId());
    }
    return find(home, date, meal);
  }

  /** Called inside the existing IdempotencyExecutor transaction. */
  public List<PlanSlot> commit(UUID actor, UUID home, List<PlanSlotWrite> slots) {
    if (slots == null || slots.isEmpty() || slots.size() > 42)
      throw new IllegalArgumentException("Invalid slots");
    for (PlanSlotWrite slot : slots) {
      if (slot == null || slot.getMealType() == null || slot.getExpectedVersion() == null
          || slot.getExpectedVersion() < 0 || slot.getItems() == null) {
        throw new IllegalArgumentException("Invalid slot");
      }
      BdRules.date(slot.getDate());
    }
    List<PlanSlotWrite> sorted = new ArrayList<>(slots);
    sorted.sort(
        Comparator.comparing(PlanSlotWrite::getDate).thenComparing(s -> s.getMealType().ordinal()));
    Set<String> seen = new HashSet<>();
    for (PlanSlotWrite s : sorted)
      if (!seen.add(s.getDate() + s.getMealType()))
        throw new IllegalArgumentException("Duplicate slot");
    BdRules.range(sorted.get(0).getDate(), sorted.get(sorted.size() - 1).getDate(), 14);
    requireMember(actor, home, true);
    List<PlanSlot> result = new ArrayList<>();
    for (PlanSlotWrite s : sorted)
      result.add(write(home, s.getDate(), s.getMealType(), s.getItems(), s.getExpectedVersion()));
    return result;
  }

  private PlanSlot write(
      UUID home, String date, PlanMealType meal, List<PlanItem> items, long expected) {
    BdRules.date(date);
    if (meal == null || expected < 0 || items == null || items.size() > 10)
      throw new IllegalArgumentException("Invalid slot");
    Set<UUID> seen = new HashSet<>();
    for (PlanItem item : items) {
      if (item == null || item.getRecipeId() == null || !seen.add(item.getRecipeId()))
        throw new IllegalArgumentException("Duplicate recipe");
      BdRules.decimal(item.getServings(), true);
      // Read-only reference validation against the existing shared schema; no AC mutations.
      Boolean active =
          jdbc.queryForObject(
              "SELECT EXISTS(SELECT 1 FROM recipes WHERE id=? AND household_id=? AND NOT archived)",
              Boolean.class,
              item.getRecipeId(),
              home);
      if (!Boolean.TRUE.equals(active)) throw new BdRules.Failure("NOT_FOUND");
    }
    PlanSlot old = find(home, date, meal);
    if ((old == null ? 0 : old.getVersion()) != expected)
      throw new BdRules.Failure("VERSION_CONFLICT");
    if (old != null && old.getLocked()) throw new BdRules.Failure("PLAN_LOCKED");
    UUID id = old == null ? UUID.randomUUID() : old.getId();
    if (old == null)
      jdbc.update(
          "INSERT INTO plan_slots(id,household_id,date,meal_type) VALUES (?,?,?::date,?)",
          id,
          home,
          date,
          meal.getValue());
    else
      jdbc.update(
          "UPDATE plan_slots SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
    jdbc.update("DELETE FROM plan_items WHERE slot_id=?", id);
    int position = 0;
    for (PlanItem item : items)
      jdbc.update(
          "INSERT INTO plan_items(slot_id,household_id,recipe_id,position,servings) VALUES"
              + " (?,?,?,?,?)",
          id,
          home,
          item.getRecipeId(),
          position++,
          BdRules.decimal(item.getServings(), true));
    return find(home, date, meal);
  }

  private PlanSlot find(UUID home, String date, PlanMealType meal) {
    List<PlanSlot> rows =
        jdbc.query(
            "SELECT * FROM plan_slots WHERE household_id=? AND date=?::date AND meal_type=?",
            this::map,
            home,
            date,
            meal.getValue());
    return rows.isEmpty() ? null : rows.get(0);
  }

  private PlanSlot map(ResultSet rs, int row) throws SQLException {
    UUID id = rs.getObject("id", UUID.class);
    List<PlanItem> items =
        jdbc.query(
            "SELECT recipe_id,servings FROM plan_items WHERE slot_id=? ORDER BY position",
            (r, n) ->
                new PlanItem()
                    .recipeId(r.getObject(1, UUID.class))
                    .servings(BdRules.number(r.getBigDecimal(2))),
            id);
    return new PlanSlot()
        .id(id)
        .createdAt(TIME.format(rs.getTimestamp("created_at").toInstant()))
        .updatedAt(TIME.format(rs.getTimestamp("updated_at").toInstant()))
        .householdId(rs.getObject("household_id", UUID.class))
        .date(rs.getDate("date").toString())
        .mealType(PlanMealType.fromValue(rs.getString("meal_type")))
        .items(items)
        .locked(rs.getBoolean("locked"))
        .version(rs.getLong("version"));
  }

  public static void pagination(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new IllegalArgumentException("Invalid pagination");
  }
}
