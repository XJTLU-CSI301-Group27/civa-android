package edu.xjtlu.civa.server.records;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.xjtlu.civa.server.api.model.*;
import edu.xjtlu.civa.server.planning.BdRules;
import edu.xjtlu.civa.server.planning.PlanningService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordsService {
  private final JdbcTemplate jdbc;
  private final PlanningService access;
  private final ObjectMapper json;
  private final Clock clock;
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  public RecordsService(JdbcTemplate jdbc, PlanningService access, ObjectMapper json) {
    this.jdbc = jdbc;
    this.access = access;
    this.json = json;
    this.clock = Clock.systemUTC();
  }

  @Transactional(readOnly = true)
  public MealEntryPage list(UUID actor, String from, String to, int page, int size) {
    BdRules.range(from, to, 366);
    PlanningService.pagination(page, size);
    Long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM meal_entries WHERE user_id=? AND date BETWEEN ?::date AND"
                + " ?::date",
            Long.class,
            actor,
            from,
            to);
    List<MealEntry> entries =
        jdbc.query(
            "SELECT * FROM meal_entries WHERE user_id=? AND date BETWEEN ?::date AND ?::date ORDER"
                + " BY date DESC,CASE meal_type WHEN 'BREAKFAST' THEN 0 WHEN 'LUNCH' THEN 1 WHEN"
                + " 'DINNER' THEN 2 ELSE 3 END,created_at DESC,id LIMIT ? OFFSET ?",
            this::map,
            actor,
            from,
            to,
            size,
            (long) page * size);
    return new MealEntryPage().items(entries).page(page).size(size).total(total);
  }

  /** Called inside the existing idempotency transaction; never touches inventory. */
  public MealEntry create(UUID actor, CreateMealEntryRequest request) {
    UUID id = UUID.randomUUID();
    Values v =
        validate(
            actor,
            request.getDate(),
            request.getMealType(),
            request.getName(),
            request.getAmount(),
            request.getBasis(),
            request.getBasisKcal(),
            request.getNote(),
            request.getRecipeId(),
            request.getCookingEventId(),
            request.getImageId(),
            null);
    jdbc.update(
        "INSERT INTO"
            + " meal_entries(id,user_id,date,zone_id,meal_type,name,recipe_id,cooking_event_id,amount,basis,basis_kcal,total_kcal,note,image_id)"
            + " VALUES (?,?,?::date,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        actor,
        request.getDate(),
        v.zone,
        request.getMealType().getValue(),
        request.getName().trim(),
        v.recipe,
        request.getCookingEventId(),
        BdRules.decimal(request.getAmount(), true),
        request.getBasis().getValue(),
        decimalOrNull(request.getBasisKcal()),
        decimalOrNull(v.total),
        request.getNote(),
        request.getImageId());
    return get(actor, id, false);
  }

  @Transactional
  public MealEntry update(UUID actor, UUID id, UpdateMealEntryRequest request) {
    MealEntry old = get(actor, id, true);
    if (!old.getVersion().equals(request.getExpectedVersion()))
      throw new BdRules.Failure("VERSION_CONFLICT");
    Values v =
        validate(
            actor,
            request.getDate(),
            request.getMealType(),
            request.getName(),
            request.getAmount(),
            request.getBasis(),
            request.getBasisKcal(),
            request.getNote(),
            request.getRecipeId(),
            request.getCookingEventId(),
            request.getImageId(),
            old);
    jdbc.update(
        "UPDATE meal_entries SET"
            + " date=?::date,meal_type=?,name=?,recipe_id=?,cooking_event_id=?,amount=?,basis=?,basis_kcal=?,total_kcal=?,note=?,image_id=?,version=version+1,updated_at=CURRENT_TIMESTAMP"
            + " WHERE id=? AND user_id=?",
        request.getDate(),
        request.getMealType().getValue(),
        request.getName().trim(),
        v.recipe,
        request.getCookingEventId(),
        BdRules.decimal(request.getAmount(), true),
        request.getBasis().getValue(),
        decimalOrNull(request.getBasisKcal()),
        decimalOrNull(v.total),
        request.getNote(),
        request.getImageId(),
        id,
        actor);
    return get(actor, id, false);
  }

  @Transactional
  public void delete(UUID actor, UUID id, long version) {
    MealEntry old = get(actor, id, true);
    if (old.getVersion() != version) throw new BdRules.Failure("VERSION_CONFLICT");
    jdbc.update("DELETE FROM meal_entries WHERE id=? AND user_id=?", id, actor);
  }

  @Transactional(readOnly = true)
  public PeriodSummary summary(UUID actor, String from, String to) {
    BdRules.range(from, to, 366);
    Map<LocalDate, DaySummary> indexed = new HashMap<>();
    jdbc.query(
        "SELECT date,COALESCE(sum(total_kcal),0) AS kcal,count(*) FILTER (WHERE total_kcal IS NULL)"
            + " AS unknown,count(*) AS entries FROM meal_entries WHERE user_id=? AND date BETWEEN"
            + " ?::date AND ?::date GROUP BY date",
        rs -> {
          String date = rs.getDate("date").toString();
          indexed.put(
              LocalDate.parse(date),
              new DaySummary()
                  .date(date)
                  .knownKcal(BdRules.number(rs.getBigDecimal("kcal")))
                  .unknownCount(rs.getInt("unknown"))
                  .entryCount(rs.getInt("entries")));
        },
        actor,
        from,
        to);
    List<DaySummary> days = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    int unknown = 0;
    for (LocalDate day = BdRules.date(from);
        !day.isAfter(BdRules.date(to));
        day = day.plusDays(1)) {
      DaySummary summary =
          indexed.getOrDefault(
              day,
              new DaySummary().date(day.toString()).knownKcal("0").unknownCount(0).entryCount(0));
      days.add(summary);
      total = total.add(new BigDecimal(summary.getKnownKcal()));
      unknown += summary.getUnknownCount();
    }
    return new PeriodSummary()
        .from(from)
        .to(to)
        .knownKcal(BdRules.number(total))
        .unknownCount(unknown)
        .averageKnownKcal(
            BdRules.number(total.divide(BigDecimal.valueOf(days.size()), 3, RoundingMode.HALF_UP)))
        .days(days);
  }

  @Transactional(readOnly = true)
  public CookingEventPage cooking(
      UUID actor, UUID home, UUID recipe, String from, String to, int page, int size) {
    access.requireMember(actor, home, false);
    PlanningService.pagination(page, size);
    if (from != null) BdRules.date(from);
    if (to != null) BdRules.date(to);
    if (from != null && to != null && BdRules.date(from).isAfter(BdRules.date(to)))
      throw new IllegalArgumentException("Invalid date range");
    StringBuilder where =
        new StringBuilder(
            " FROM cooking_events c JOIN households h ON h.id=c.household_id WHERE"
                + " c.household_id=?");
    List<Object> params = new ArrayList<>();
    params.add(home);
    if (recipe != null) {
      where.append(" AND c.recipe_id=?");
      params.add(recipe);
    }
    if (from != null) {
      where.append(" AND (c.cooked_at AT TIME ZONE h.zone_id)::date>=?::date");
      params.add(from);
    }
    if (to != null) {
      where.append(" AND (c.cooked_at AT TIME ZONE h.zone_id)::date<=?::date");
      params.add(to);
    }
    long total = jdbc.queryForObject("SELECT count(*)" + where, Long.class, params.toArray());
    List<Object> paged = new ArrayList<>(params);
    paged.add(size);
    paged.add((long) page * size);
    List<CookingEvent> items =
        jdbc.query(
            "SELECT c.*" + where + " ORDER BY c.created_at DESC,c.id LIMIT ? OFFSET ?",
            (rs, n) -> {
              try {
                return new CookingEvent()
                    .id(rs.getObject("id", UUID.class))
                    .createdAt(time(rs, "created_at"))
                    .updatedAt(time(rs, "updated_at"))
                    .householdId(home)
                    .actorId(rs.getObject("actor_id", UUID.class))
                    .cookedAt(time(rs, "cooked_at"))
                    .servings(BdRules.number(rs.getBigDecimal("servings")))
                    .recipe(json.readValue(rs.getString("recipe_snapshot"), RecipeSnapshot.class))
                    .planSlotId(rs.getObject("plan_slot_id", UUID.class))
                    .requestId(rs.getObject("request_id", UUID.class));
              } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Invalid stored recipe snapshot", ex);
              }
            },
            paged.toArray());
    return new CookingEventPage().items(items).page(page).size(size).total(total);
  }

  private Values validate(
      UUID actor,
      String date,
      EntryMealType meal,
      String name,
      String amount,
      KcalBasis basis,
      String kcal,
      String note,
      UUID recipe,
      UUID cooking,
      UUID image,
      MealEntry old) {
    String zone = jdbc.queryForObject("SELECT zone_id FROM users WHERE id=?", String.class, actor);
    if (BdRules.date(date).isAfter(LocalDate.now(clock.withZone(ZoneId.of(zone)))))
      throw new IllegalArgumentException("Future meals are plans, not records");
    if (meal == null
        || basis == null
        || name == null
        || name.trim().isEmpty()
        || name.trim().codePointCount(0, name.trim().length()) > 120
        || note == null
        || note.codePointCount(0, note.length()) > 2000)
      throw new IllegalArgumentException("Invalid meal");
    String total = BdRules.calories(basis.getValue(), amount, kcal);
    // Unchanged historical links stay valid after leaving a household. New links require access.
    boolean same =
        old != null
            && Objects.equals(recipe, old.getRecipeId())
            && Objects.equals(cooking, old.getCookingEventId());
    if (!same) {
      if (cooking != null) {
        List<UUID> recipes =
            jdbc.query(
                "SELECT c.recipe_id FROM cooking_events c JOIN memberships m ON"
                    + " m.household_id=c.household_id AND m.user_id=? AND m.active WHERE c.id=?",
                (rs, n) -> rs.getObject(1, UUID.class),
                actor,
                cooking);
        if (recipes.isEmpty()) throw new BdRules.Failure("NOT_FOUND");
        if (recipe != null && !recipe.equals(recipes.get(0)))
          throw new IllegalArgumentException("Recipe and cooking event disagree");
        recipe = recipes.get(0);
      }
      if (recipe != null
          && !Boolean.TRUE.equals(
              jdbc.queryForObject(
                  "SELECT EXISTS(SELECT 1 FROM recipes r JOIN memberships m ON"
                      + " m.household_id=r.household_id AND m.user_id=? AND m.active WHERE r.id=?)",
                  Boolean.class,
                  actor,
                  recipe))) throw new BdRules.Failure("NOT_FOUND");
    }
    if (image != null
        && !Objects.equals(image, old == null ? null : old.getImageId())
        && !Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM media WHERE id=? AND owner_user_id=? AND"
                    + " scope='PERSONAL')",
                Boolean.class,
                image,
                actor))) throw new BdRules.Failure("NOT_FOUND");
    return new Values(zone, total, recipe);
  }

  private MealEntry get(UUID actor, UUID id, boolean lock) {
    List<MealEntry> entries =
        jdbc.query(
            "SELECT * FROM meal_entries WHERE id=? AND user_id=?" + (lock ? " FOR UPDATE" : ""),
            this::map,
            id,
            actor);
    if (entries.isEmpty()) throw new BdRules.Failure("NOT_FOUND");
    return entries.get(0);
  }

  private MealEntry map(ResultSet rs, int row) throws SQLException {
    return new MealEntry()
        .id(rs.getObject("id", UUID.class))
        .createdAt(time(rs, "created_at"))
        .updatedAt(time(rs, "updated_at"))
        .userId(rs.getObject("user_id", UUID.class))
        .date(rs.getDate("date").toString())
        .zoneId(rs.getString("zone_id"))
        .mealType(EntryMealType.fromValue(rs.getString("meal_type")))
        .name(rs.getString("name"))
        .recipeId(rs.getObject("recipe_id", UUID.class))
        .cookingEventId(rs.getObject("cooking_event_id", UUID.class))
        .amount(BdRules.number(rs.getBigDecimal("amount")))
        .basis(KcalBasis.fromValue(rs.getString("basis")))
        .basisKcal(numberOrNull(rs.getBigDecimal("basis_kcal")))
        .totalKcal(numberOrNull(rs.getBigDecimal("total_kcal")))
        .note(rs.getString("note"))
        .imageId(rs.getObject("image_id", UUID.class))
        .version(rs.getLong("version"));
  }

  private static BigDecimal decimalOrNull(String value) {
    return value == null ? null : BdRules.decimal(value, false);
  }

  private static String numberOrNull(BigDecimal value) {
    return value == null ? null : BdRules.number(value);
  }

  private static String time(ResultSet rs, String field) throws SQLException {
    return TIME.format(rs.getTimestamp(field).toInstant());
  }

  private record Values(String zone, String total, UUID recipe) {}
}
