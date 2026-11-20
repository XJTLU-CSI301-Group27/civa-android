package edu.xjtlu.civa.server.household;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The single membership check every business package uses.
 *
 * <p>It belongs to household, not to whichever feature happened to need it first: any package may
 * call it, and a non-member gets NOT_FOUND or HOUSEHOLD_ACCESS_REVOKED from one implementation.
 */
@Service
public class HouseholdAccess {
  private final JdbcTemplate jdbc;

  public HouseholdAccess(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Rechecked on every request, including idempotent replay. */
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
    if (rows.isEmpty()) throw new HouseholdReadService.AccessFailure("NOT_FOUND");
    if (!rows.get(0)) throw new HouseholdReadService.AccessFailure("HOUSEHOLD_ACCESS_REVOKED");
  }
}
