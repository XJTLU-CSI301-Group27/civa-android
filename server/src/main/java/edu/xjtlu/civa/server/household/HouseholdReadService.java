package edu.xjtlu.civa.server.household;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Internal queries; callers must supply the authenticated user's ID. */
final class HouseholdReadService {
    private final JdbcTemplate jdbc;
    HouseholdReadService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Household getHousehold(UUID actorId, UUID householdId) {
        List<Household> rows = jdbc.query("SELECT h.id,h.name,h.zone_id,h.version,h.created_at,h.updated_at FROM households h JOIN memberships a ON a.household_id=h.id AND a.user_id=? AND a.active WHERE h.id=?",
                (rs, n) -> new Household(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getTimestamp(5).toInstant(), rs.getTimestamp(6).toInstant()), actorId, householdId);
        if (rows.isEmpty()) throw denied(actorId, householdId);
        return rows.get(0);
    }

    MemberPage listMembers(UUID actorId, UUID householdId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
        String query = "WITH auth AS (SELECT household_id FROM memberships WHERE user_id=? AND household_id=? AND active), "
                + "members AS (SELECT * FROM memberships WHERE household_id=? AND active), "
                + "selected AS (SELECT * FROM members ORDER BY created_at DESC,id ASC LIMIT ? OFFSET ?) "
                + "SELECT (SELECT count(*) FROM members),s.id,s.user_id,s.role,s.active,s.version,s.created_at,s.updated_at "
                + "FROM auth LEFT JOIN selected s ON true ORDER BY s.created_at DESC,s.id ASC";
        List<Member> members = new ArrayList<>();
        long[] total = {-1};
        jdbc.query(query, rs -> {
            total[0] = rs.getLong(1);
            UUID id = rs.getObject(2, UUID.class);
            if (id != null) members.add(new Member(id, householdId, rs.getObject(3, UUID.class), rs.getString(4),
                    rs.getBoolean(5), rs.getLong(6), rs.getTimestamp(7).toInstant(), rs.getTimestamp(8).toInstant()));
        }, actorId, householdId, householdId, size, (long) page * size);
        if (total[0] < 0) throw denied(actorId, householdId);
        return new MemberPage(members, page, size, total[0]);
    }

    private AccessFailure denied(UUID actorId, UUID householdId) {
        Boolean revoked = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM memberships WHERE user_id=? AND household_id=? AND NOT active)",
                Boolean.class, actorId, householdId);
        return new AccessFailure(Boolean.TRUE.equals(revoked) ? "HOUSEHOLD_ACCESS_REVOKED" : "NOT_FOUND");
    }

    static final class AccessFailure extends RuntimeException {
        final String code;
        AccessFailure(String code) { super(code); this.code = code; }
    }
    static final class Household {
        final UUID id;
        final String name, zoneId;
        final long version;
        final Instant createdAt, updatedAt;
        Household(UUID id, String name, String zoneId, long version, Instant createdAt, Instant updatedAt) {
            this.id = id; this.name = name; this.zoneId = zoneId; this.version = version;
            this.createdAt = createdAt; this.updatedAt = updatedAt;
        }
    }
    static final class Member {
        final UUID id, householdId, userId;
        final String role;
        final boolean active;
        final long version;
        final Instant createdAt, updatedAt;
        Member(UUID id, UUID householdId, UUID userId, String role, boolean active, long version, Instant createdAt, Instant updatedAt) {
            this.id = id; this.householdId = householdId; this.userId = userId; this.role = role; this.active = active;
            this.version = version; this.createdAt = createdAt; this.updatedAt = updatedAt;
        }
    }
    static final class MemberPage {
        final List<Member> items;
        final int page, size;
        final long total;
        MemberPage(List<Member> items, int page, int size, long total) {
            this.items = List.copyOf(items); this.page = page; this.size = size; this.total = total;
        }
    }
}
