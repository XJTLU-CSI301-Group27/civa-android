package edu.xjtlu.civa.server.household;

import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.xjtlu.civa.server.application.IdempotencyExecutor;
import edu.xjtlu.civa.server.config.JsonbCodec;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Candidate writes; callers must authenticate and derive the digest server-side from the request. */
public final class HouseholdWriteService {
    private static final DateTimeFormatter WIRE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private final JdbcTemplate jdbc;
    private final IdempotencyExecutor idempotency;
    private final JsonbCodec json;
    private final Clock clock;

    public HouseholdWriteService(JdbcTemplate jdbc, IdempotencyExecutor idempotency, JsonbCodec json, Clock clock) {
        this.jdbc = jdbc; this.idempotency = idempotency; this.json = json; this.clock = clock;
    }

    public IdempotencyExecutor.Result createHousehold(UUID actor, UUID key, byte[] digest, String requestedName) {
        String name = validatedName(requestedName);
        return idempotency.execute(actor, key, digest, () -> {
            if (hasActiveHousehold(actor)) throw new Failure("ALREADY_IN_HOUSEHOLD");
            UUID id = UUID.randomUUID();
            List<ObjectNode> homes = jdbc.query("INSERT INTO households(id,name) VALUES (?,?) RETURNING id,name,zone_id,version,created_at,updated_at",
                    (rs, n) -> householdJson(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getLong(4),
                            rs.getTimestamp(5).toInstant(), rs.getTimestamp(6).toInstant()), id, name);
            int inserted = jdbc.update("INSERT INTO memberships(id,household_id,user_id,role,active) VALUES (?,?,?,'OWNER',true) ON CONFLICT (user_id) WHERE active DO NOTHING",
                    UUID.randomUUID(), id, actor);
            if (inserted != 1) throw new Failure("ALREADY_IN_HOUSEHOLD");
            return new IdempotencyExecutor.Result(201, json.write(homes.get(0)));
        }, result -> requireReplayMembership(actor, result));
    }

    public IdempotencyExecutor.Result joinHousehold(UUID actor, UUID key, byte[] digest, String invitationCode) {
        byte[] codeHash;
        try { codeHash = InvitationCodes.hash(invitationCode); }
        catch (IllegalArgumentException ex) { throw new Failure("INVALID_INVITATION"); }
        return idempotency.execute(actor, key, digest, () -> {
            List<Invitation> lookup = jdbc.query("SELECT id,household_id FROM invitations WHERE code_hash=?",
                    (rs, n) -> new Invitation(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), codeHash);
            if (lookup.isEmpty()) throw new Failure("INVALID_INVITATION");
            Invitation invitation = lookup.get(0);
            lockHousehold(invitation.householdId);
            List<Boolean> valid = jdbc.query("SELECT used_at IS NULL AND expires_at > ? FROM invitations WHERE id=? AND code_hash=? FOR UPDATE",
                    (rs, n) -> rs.getBoolean(1), Timestamp.from(clock.instant()), invitation.id, codeHash);
            if (valid.isEmpty() || !valid.get(0)) throw new Failure("INVALID_INVITATION");
            if (hasActiveHousehold(actor)) throw new Failure("ALREADY_IN_HOUSEHOLD");
            UUID id = UUID.randomUUID();
            List<ObjectNode> members = jdbc.query("INSERT INTO memberships(id,household_id,user_id,role,active) VALUES (?,?,?,'MEMBER',true) "
                            + "ON CONFLICT (user_id) WHERE active DO NOTHING RETURNING id,household_id,user_id,role,active,version,created_at,updated_at",
                    (rs, n) -> memberJson(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                            rs.getString(4), rs.getBoolean(5), rs.getLong(6), rs.getTimestamp(7).toInstant(), rs.getTimestamp(8).toInstant()),
                    id, invitation.householdId, actor);
            if (members.isEmpty()) throw new Failure("ALREADY_IN_HOUSEHOLD");
            jdbc.update("UPDATE invitations SET used_at=? WHERE id=?", Timestamp.from(clock.instant()), invitation.id);
            return new IdempotencyExecutor.Result(201, json.write(members.get(0)));
        }, result -> requireReplayMembership(actor, result));
    }

    private void requireReplayMembership(UUID actor, IdempotencyExecutor.Result result) {
        try {
            var body = json.read(result.body);
            String field = body.has("householdId") ? "householdId" : "id";
            UUID household = UUID.fromString(body.path(field).asText());
            lockHousehold(household);
            List<String> roles = jdbc.query("SELECT role FROM memberships WHERE household_id=? AND user_id=? AND active",
                    (rs, n) -> rs.getString(1), household, actor);
            if (roles.isEmpty()) {
                Boolean revoked = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM memberships WHERE household_id=? AND user_id=? AND NOT active)", Boolean.class, household, actor);
                throw new Failure(Boolean.TRUE.equals(revoked) ? "HOUSEHOLD_ACCESS_REVOKED" : "NOT_FOUND");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid stored household response");
        }
    }

    private void lockHousehold(UUID householdId) {
        List<UUID> rows = jdbc.query("SELECT id FROM households WHERE id=? FOR UPDATE", (rs, n) -> rs.getObject(1, UUID.class), householdId);
        if (rows.isEmpty()) throw new Failure("NOT_FOUND");
    }

    private boolean hasActiveHousehold(UUID actor) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM memberships WHERE user_id=? AND active)", Boolean.class, actor));
    }

    private static String validatedName(String requested) {
        if (requested == null) throw new IllegalArgumentException("Invalid household name");
        String name = requested.trim();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 120) throw new IllegalArgumentException("Invalid household name");
        return name;
    }

    private ObjectNode householdJson(UUID id, String name, String zoneId, long version, Instant created, Instant updated) {
        ObjectNode body = json.object();
        body.put("id", id.toString()); body.put("name", name); body.put("zoneId", zoneId); body.put("version", version);
        body.put("createdAt", WIRE_TIME.format(created)); body.put("updatedAt", WIRE_TIME.format(updated));
        return body;
    }

    private ObjectNode memberJson(UUID id, UUID household, UUID user, String role, boolean active, long version, Instant created, Instant updated) {
        ObjectNode body = json.object();
        body.put("id", id.toString()); body.put("householdId", household.toString()); body.put("userId", user.toString());
        body.put("role", role); body.put("active", active); body.put("version", version);
        body.put("createdAt", WIRE_TIME.format(created)); body.put("updatedAt", WIRE_TIME.format(updated));
        return body;
    }

    public static final class Failure extends RuntimeException {
        public final String code;
        Failure(String code) { super(code); this.code = code; }
    }
    private static final class Invitation {
        final UUID id, householdId;
        Invitation(UUID id, UUID householdId) { this.id = id; this.householdId = householdId; }
    }
}
