package edu.xjtlu.civa.server.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.xjtlu.civa.server.api.model.*;
import edu.xjtlu.civa.server.application.IdempotencyExecutor;
import edu.xjtlu.civa.server.config.JsonbCodec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Package-private inventory implementation; generated DTOs remain the sole wire models. */
final class InventoryService {
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
  final JdbcTemplate jdbc;
  private final IdempotencyExecutor idempotency;
  private final ObjectMapper json;
  private final JsonbCodec jsonb;
  private final Clock clock;
  private final TransactionTemplate reads;
  private final TransactionTemplate writes;

  InventoryService(
      JdbcTemplate jdbc,
      PlatformTransactionManager manager,
      IdempotencyExecutor idempotency,
      ObjectMapper json,
      JsonbCodec jsonb,
      Clock clock) {
    this.jdbc = jdbc;
    this.idempotency = idempotency;
    this.json = json;
    this.clock = clock;
    this.jsonb = jsonb;
    reads = new TransactionTemplate(manager);
    reads.setReadOnly(true);
    reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    writes = new TransactionTemplate(manager);
  }

  IngredientPage ingredients(String query, int page, int size) {
    InventoryRules.page(page, size);
    String q = query == null || query.isBlank() ? "" : IngredientNames.normalize(query);
    return reads.execute(
        tx -> {
          String filter =
              " FROM ingredients i WHERE i.active AND (?='' OR position(? in"
                  + " lower(regexp_replace(trim(normalize(i.name,NFKC)), '\\s+', ' ', 'g')))>0 OR"
                  + " EXISTS (SELECT 1 FROM ingredient_aliases a WHERE a.ingredient_id=i.id AND"
                  + " position(? in a.normalized_name)>0))";
          long total = jdbc.queryForObject("SELECT count(*)" + filter, Long.class, q, q, q);
          List<Ingredient> items =
              jdbc.query(
                  "SELECT i.*" + filter + " ORDER BY i.name,i.id LIMIT ? OFFSET ?",
                  (rs, n) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    List<String> aliases =
                        jdbc.query(
                            "SELECT display_name FROM ingredient_aliases WHERE ingredient_id=?"
                                + " ORDER BY normalized_name",
                            (a, k) -> a.getString(1),
                            id);
                    return new Ingredient()
                        .id(id)
                        .name(rs.getString("name"))
                        .unit(Unit.fromValue(rs.getString("unit")))
                        .active(true)
                        .aliases(aliases);
                  },
                  q,
                  q,
                  q,
                  size,
                  (long) page * size);
          return new IngredientPage(items, page, size, total);
        });
  }

  IdempotencyExecutor.Result createIngredient(
      UUID actor, UUID key, byte[] digest, CreateIngredientRequest body) {
    String normalized = IngredientNames.normalize(body.getName());
    if (body.getUnit() == null
        || !java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(body.getName()))
      throw new IllegalArgumentException("Valid name and unit required");
    String display =
        java.text.Normalizer.normalize(body.getName().trim(), java.text.Normalizer.Form.NFKC)
            .replaceAll("\\s+", " ");
    return idempotency.execute(
        actor,
        key,
        digest,
        () -> {
          authorizeIngredientCreation(actor);
          // Across households, equivalent names serialize without migrating old ingredient IDs.
          jdbc.query(
              "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
              ps -> ps.setString(1, "civa:ingredient:" + normalized),
              rs -> {});
          List<UUID> ids =
              jdbc.query(
                  "SELECT ingredient_id FROM ingredient_aliases WHERE normalized_name=?",
                  (rs, n) -> rs.getObject(1, UUID.class),
                  normalized);
          if (ids.isEmpty()) {
            ids =
                jdbc.query(
                    "SELECT id FROM ingredients WHERE"
                        + " lower(regexp_replace(trim(normalize(name,NFKC)), '\\s+', ' ', 'g'))=?"
                        + " ORDER BY id",
                    (rs, n) -> rs.getObject(1, UUID.class),
                    normalized);
          }
          if (!ids.isEmpty()) {
            Ingredient existing = ingredient(ids.get(0));
            if (!Boolean.TRUE.equals(existing.getActive()))
              throw new InventoryFailure("INGREDIENT_INACTIVE");
            if (existing.getUnit() != body.getUnit()) throw new InventoryFailure("UNIT_CONFLICT");
            return new IdempotencyExecutor.Result(200, jsonb.write(json.valueToTree(existing)));
          }
          UUID id = UUID.randomUUID();
          jdbc.update(
              "INSERT INTO ingredients(id,name,unit,active) VALUES (?,?,?,true)",
              id,
              display,
              body.getUnit().getValue());
          jdbc.update(
              "INSERT INTO ingredient_aliases(normalized_name,ingredient_id,display_name) VALUES"
                  + " (?,?,?) ON CONFLICT(normalized_name) DO NOTHING",
              normalized,
              id,
              display);
          UUID bound =
              jdbc.queryForObject(
                  "SELECT ingredient_id FROM ingredient_aliases WHERE normalized_name=?",
                  UUID.class,
                  normalized);
          if (!id.equals(bound)) throw new InventoryFailure("NAME_CONFLICT");
          return stored(ingredient(id));
        },
        result -> authorizeIngredientCreation(actor));
  }

  private void authorizeIngredientCreation(UUID actor) {
    List<UUID> homes =
        jdbc.query(
            "SELECT household_id FROM memberships WHERE user_id=? AND active ORDER BY household_id",
            (rs, n) -> rs.getObject(1, UUID.class),
            actor);
    if (homes.isEmpty()) throw new InventoryFailure("HOUSEHOLD_REQUIRED");
    authorize(actor, homes.get(0), true);
  }

  private Ingredient ingredient(UUID id) {
    Ingredient value =
        jdbc.queryForObject(
            "SELECT id,name,unit,active FROM ingredients WHERE id=?",
            (rs, n) ->
                new Ingredient()
                    .id(rs.getObject("id", UUID.class))
                    .name(rs.getString("name"))
                    .unit(Unit.fromValue(rs.getString("unit")))
                    .active(rs.getBoolean("active")),
            id);
    value.setAliases(
        jdbc.query(
            "SELECT display_name FROM ingredient_aliases WHERE ingredient_id=? ORDER BY"
                + " normalized_name",
            (rs, n) -> rs.getString(1),
            id));
    return value;
  }

  InventoryBatchPage batches(
      UUID actor, UUID home, UUID ingredient, ExpiryStatus status, int page, int size) {
    InventoryRules.page(page, size);
    return reads.execute(
        tx -> {
          LocalDate today = today(authorize(actor, home, false));
          StringBuilder filter = new StringBuilder(" FROM inventory_batches WHERE household_id=?");
          List<Object> args = new ArrayList<>();
          args.add(home);
          if (ingredient != null) {
            filter.append(" AND ingredient_id=?");
            args.add(ingredient);
          }
          if (status == ExpiryStatus.EXPIRED) {
            filter.append(" AND expires_on < ?");
            args.add(today);
          }
          if (status == ExpiryStatus.SOON) {
            filter.append(" AND expires_on BETWEEN ? AND ?");
            args.add(today);
            args.add(today.plusDays(3));
          }
          if (status == ExpiryStatus.VALID) {
            filter.append(" AND (expires_on IS NULL OR expires_on>=?)");
            args.add(today);
          }
          long total = jdbc.queryForObject("SELECT count(*)" + filter, Long.class, args.toArray());
          args.add(size);
          args.add((long) page * size);
          List<InventoryBatch> rows =
              jdbc.query(
                  "SELECT *"
                      + filter
                      + " ORDER BY expires_on ASC NULLS LAST,created_at,id LIMIT ? OFFSET ?",
                  InventoryService::batch,
                  args.toArray());
          return new InventoryBatchPage(rows, page, size, total);
        });
  }

  InventoryMovementPage movements(UUID actor, UUID home, UUID batch, int page, int size) {
    InventoryRules.page(page, size);
    return reads.execute(
        tx -> {
          authorize(actor, home, false);
          String filter =
              " FROM inventory_movements WHERE household_id=?"
                  + (batch == null ? "" : " AND batch_id=?");
          List<Object> args = new ArrayList<>();
          args.add(home);
          if (batch != null) args.add(batch);
          long total = jdbc.queryForObject("SELECT count(*)" + filter, Long.class, args.toArray());
          args.add(size);
          args.add((long) page * size);
          List<InventoryMovement> rows =
              jdbc.query(
                  "SELECT *" + filter + " ORDER BY created_at DESC,id ASC LIMIT ? OFFSET ?",
                  InventoryService::movement,
                  args.toArray());
          return new InventoryMovementPage(rows, page, size, total);
        });
  }

  IdempotencyExecutor.Result create(
      UUID actor, UUID home, UUID key, byte[] digest, CreateInventoryBatchRequest body) {
    var quantity = InventoryRules.quantity(body.getQuantity());
    InventoryRules.location(body.getLocation());
    LocalDate expiry = InventoryRules.date(body.getExpiresOn());
    if (body.getIngredientId() == null || body.getUnit() == null)
      throw new IllegalArgumentException("Ingredient and unit required");
    return idempotency.execute(
        actor,
        key,
        digest,
        () -> {
          authorize(actor, home, true);
          List<String> units =
              jdbc.query(
                  "SELECT unit FROM ingredients WHERE id=? AND active",
                  (rs, n) -> rs.getString(1),
                  body.getIngredientId());
          if (units.isEmpty() || !units.get(0).equals(body.getUnit().getValue()))
            throw new IllegalArgumentException("Invalid ingredient or unit");
          InventoryBatch result =
              jdbc.queryForObject(
                  "INSERT INTO"
                      + " inventory_batches(id,household_id,ingredient_id,quantity,unit,location,expires_on)"
                      + " VALUES (?,?,?,?,?,?,?) RETURNING *",
                  InventoryService::batch,
                  UUID.randomUUID(),
                  home,
                  body.getIngredientId(),
                  quantity,
                  body.getUnit().getValue(),
                  body.getLocation(),
                  expiry);
          return stored(result);
        },
        result -> authorize(actor, home, true));
  }

  InventoryBatch update(UUID actor, UUID home, UUID id, UpdateInventoryBatchRequest body) {
    InventoryRules.location(body.getLocation());
    LocalDate expiry = InventoryRules.date(body.getExpiresOn());
    return writes.execute(
        tx -> {
          authorize(actor, home, true);
          InventoryBatch before = findBatch(home, id);
          InventoryRules.version(before.getVersion(), body.getExpectedVersion());
          return jdbc.queryForObject(
              "UPDATE inventory_batches SET"
                  + " location=?,expires_on=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE"
                  + " household_id=? AND id=? RETURNING *",
              InventoryService::batch,
              body.getLocation(),
              expiry,
              home,
              id);
        });
  }

  IdempotencyExecutor.Result change(
      UUID actor, UUID home, UUID id, UUID key, byte[] digest, InventoryChangeRequest body) {
    var after = InventoryRules.quantity(body.getAfterQuantity());
    if (body.getReason() == null) throw new IllegalArgumentException("Reason required");
    return idempotency.execute(
        actor,
        key,
        digest,
        () -> {
          authorize(actor, home, true);
          InventoryBatch before = findBatch(home, id);
          InventoryRules.version(before.getVersion(), body.getExpectedVersion());
          var old = InventoryRules.quantity(before.getQuantity());
          if (body.getReason() != ChangeReason.CORRECT && after.compareTo(old) >= 0)
            throw new IllegalArgumentException("Consumption must decrease quantity");
          jdbc.update(
              "UPDATE inventory_batches SET"
                  + " quantity=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE"
                  + " household_id=? AND id=?",
              after,
              home,
              id);
          InventoryMovement result =
              jdbc.queryForObject(
                  "INSERT INTO"
                      + " inventory_movements(id,household_id,batch_id,before_quantity,after_quantity,reason,actor_id,request_id)"
                      + " VALUES (?,?,?,?,?,?,?,?) RETURNING *",
                  InventoryService::movement,
                  UUID.randomUUID(),
                  home,
                  id,
                  old,
                  after,
                  body.getReason().getValue(),
                  actor,
                  key);
          return stored(result);
        },
        result -> authorize(actor, home, true));
  }

  // Same household lock order as the shared cooking transaction: home, membership, recipe, batches.
  String authorize(UUID actor, UUID home, boolean lock) {
    if (lock) jdbc.execute("SET LOCAL lock_timeout = '5s'");
    List<String> homes =
        jdbc.query(
            "SELECT zone_id FROM households WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            (rs, n) -> rs.getString(1),
            home);
    if (homes.isEmpty()) throw new InventoryFailure("NOT_FOUND");
    Boolean member =
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM memberships WHERE household_id=? AND user_id=? AND"
                + " active)",
            Boolean.class,
            home,
            actor);
    if (!Boolean.TRUE.equals(member)) {
      Boolean removed =
          jdbc.queryForObject(
              "SELECT EXISTS(SELECT 1 FROM memberships WHERE household_id=? AND user_id=? AND NOT"
                  + " active)",
              Boolean.class,
              home,
              actor);
      throw new InventoryFailure(
          Boolean.TRUE.equals(removed) ? "HOUSEHOLD_ACCESS_REVOKED" : "NOT_FOUND");
    }
    return homes.get(0);
  }

  InventoryBatch findBatch(UUID home, UUID id) {
    List<InventoryBatch> rows =
        jdbc.query(
            "SELECT * FROM inventory_batches WHERE household_id=? AND id=? FOR UPDATE",
            InventoryService::batch,
            home,
            id);
    if (rows.isEmpty()) throw new InventoryFailure("NOT_FOUND");
    return rows.get(0);
  }

  LocalDate today(String zone) {
    return LocalDate.now(clock.withZone(ZoneId.of(zone)));
  }

  String checkedAt() {
    return TIME.format(clock.instant());
  }

  <T> T snapshot(java.util.function.Supplier<T> query) {
    return reads.execute(tx -> query.get());
  }

  private IdempotencyExecutor.Result stored(Object value) {
    return new IdempotencyExecutor.Result(201, jsonb.write(json.valueToTree(value)));
  }

  static InventoryBatch batch(ResultSet rs, int row) throws SQLException {
    var expiry = rs.getDate("expires_on");
    return new InventoryBatch()
        .id(rs.getObject("id", UUID.class))
        .householdId(rs.getObject("household_id", UUID.class))
        .ingredientId(rs.getObject("ingredient_id", UUID.class))
        .quantity(InventoryRules.number(rs.getBigDecimal("quantity")))
        .unit(Unit.fromValue(rs.getString("unit")))
        .location(rs.getString("location"))
        .expiresOn(expiry == null ? null : expiry.toLocalDate().toString())
        .version(rs.getLong("version"))
        .createdAt(TIME.format(rs.getTimestamp("created_at").toInstant()))
        .updatedAt(TIME.format(rs.getTimestamp("updated_at").toInstant()));
  }

  static InventoryMovement movement(ResultSet rs, int row) throws SQLException {
    return new InventoryMovement()
        .id(rs.getObject("id", UUID.class))
        .householdId(rs.getObject("household_id", UUID.class))
        .batchId(rs.getObject("batch_id", UUID.class))
        .beforeQuantity(InventoryRules.number(rs.getBigDecimal("before_quantity")))
        .afterQuantity(InventoryRules.number(rs.getBigDecimal("after_quantity")))
        .reason(MovementReason.fromValue(rs.getString("reason")))
        .actorId(rs.getObject("actor_id", UUID.class))
        .requestId(rs.getObject("request_id", UUID.class))
        .cookingEventId(rs.getObject("cooking_event_id", UUID.class))
        .createdAt(TIME.format(rs.getTimestamp("created_at").toInstant()))
        .updatedAt(TIME.format(rs.getTimestamp("updated_at").toInstant()));
  }
}
